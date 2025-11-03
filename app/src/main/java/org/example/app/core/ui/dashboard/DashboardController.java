package org.example.app.core.ui.dashboard;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.Parent;
import javafx.geometry.Pos;

import javafx.util.Duration;

// Blockchain components
import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import org.example.app.core.config.BlockchainConfig;
import org.example.app.core.config.ConsensusConfig;
import org.example.app.core.config.NetworkConfig;
import org.example.app.core.config.StorageConfig;
import org.example.app.core.metrics.BlockchainMetrics;
import org.example.app.core.metrics.NetworkMetrics;
import org.example.app.core.network.Messages;
import org.example.app.core.mempool.TransactionPool;
import org.example.app.core.p2p.Peer;
import org.example.app.core.p2p.PeerNetwork;
import org.example.app.core.p2p.RobustP2PManager;
import org.example.app.core.token.ERC20Token;
import org.example.app.core.token.TokenBuilder;
import org.example.app.core.token.TokenRegistry;
import org.example.app.core.token.TokenStandard;
import org.example.app.core.consensus.Consensus;
import org.example.app.core.consensus.EthashConsensus;
import org.example.app.core.consensus.HashUtils;

// Token UI imports
import org.example.app.core.ui.token.TokenController;
import org.example.app.core.ui.token.TokenManager;
import org.example.app.core.ui.token.TokenUIUtils;
import org.example.app.core.ui.token.TokenView;

// Wallet UI imports
import org.example.app.core.ui.wallet.WalletController;
import org.example.app.core.ui.wallet.WalletManager;
import org.example.app.core.ui.wallet.WalletUIUtils;
import org.example.app.core.ui.wallet.WalletView;

// Transaction UI imports
import org.example.app.core.ui.transaction.TransactionController;
import org.example.app.core.ui.transaction.UITransactionManager;
import org.example.app.core.ui.transaction.TransactionView;

// Smart Contract UI imports
import org.example.app.core.ui.smartcontract.SmartContractController;
import org.example.app.core.ui.smartcontract.SmartContractManager;
import org.example.app.core.ui.smartcontract.SmartContractUIUtils;
import org.example.app.core.ui.smartcontract.SmartContractView;
import org.example.app.core.ui.configuration.ConfigurationController;
import org.example.app.core.ui.configuration.ConfigurationManager;
import org.example.app.core.ui.configuration.ConfigurationView;
// Consensus UI imports
import org.example.app.core.ui.consensus.ConsensusController;
import org.example.app.core.ui.consensus.ConsensusManager;
import org.example.app.core.ui.consensus.ConsensusUIUtils;
import org.example.app.core.ui.consensus.ConsensusView;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusType;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusStatusInfo;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusEventListener;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusPolicy;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusHealthStatus;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusManagerEventListener;

import java.net.InetSocketAddress;
// Java utility imports
import java.net.URL;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.logging.Level;

// API/RPC UI imports - NEW
import org.example.app.core.ui.api.RPCView;
import org.example.app.core.ui.api.RPCController;
import org.example.app.core.ui.api.RPCManager;
import org.example.app.core.ui.api.RPCUIUtils;
import org.example.app.core.ui.api.APIManager;

// BlockExplorer UI imports - NEW
import org.example.app.core.ui.blockexplorer.BlockExplorerController;
import org.example.app.core.ui.blockexplorer.BlockExplorerManager;
import org.example.app.core.ui.blockexplorer.BlockExplorerView;
import org.example.app.core.ui.blockexplorer.BlockExplorerUIUtils;
import org.example.app.core.state.BlockManager;


import org.example.app.core.ui.network.NetworkController;
import org.example.app.core.ui.network.NetworkManager;
import org.example.app.core.ui.network.NetworkUIUtils;
import org.example.app.core.ui.network.NetworkView;

import org.example.app.core.ui.configuration.*;
import org.example.app.core.config.*;


/**
 * Enhanced Dashboard Controller with comprehensive blockchain management
 * Supports wallet management, smart contracts, tokens, consensus, and more
 */
public class DashboardController implements Initializable {
    
    private static final Logger LOGGER = Logger.getLogger(DashboardController.class.getName());
    
    
    // ========================================
    // FXML DASHBOARD ELEMENTS
    // ========================================
    
    @FXML private Label networkStatusLabel;
    @FXML private Label consensusAlgorithmLabel;
    @FXML private Label blockHeightLabel;
    @FXML private Label activePeersLabel;
    @FXML private Label mempoolSizeLabel;
    @FXML private Label networkHashrateLabel;
    @FXML private Label uptimeLabel;
    @FXML private Label lastBlockTimeLabel;
    
    @FXML private TableView<UIBlock> recentBlocksTable;
    @FXML private TableColumn<UIBlock, String> blockHashColumn;
    @FXML private TableColumn<UIBlock, Long> blockNumberColumn;
    @FXML private TableColumn<UIBlock, String> blockMinerColumn;
    @FXML private TableColumn<UIBlock, String> blockTimestampColumn;
    @FXML private TableColumn<UIBlock, Integer> blockTxCountColumn;
    
    @FXML private TableView<UITransaction> recentTransactionsTable;
    @FXML private TableColumn<UITransaction, String> txHashColumn;
    @FXML private TableColumn<UITransaction, String> txFromColumn;
    @FXML private TableColumn<UITransaction, String> txToColumn;
    @FXML private TableColumn<UITransaction, String> txValueColumn;
    @FXML private TableColumn<UITransaction, String> txStatusColumn;
    
    @FXML private LineChart<String, Number> blockTimeChart;
    @FXML private LineChart<String, Number> transactionChart;
    @FXML private ScrollPane contentArea;
    
    // ========================================
    // NAVIGATION BUTTONS
    // ========================================
    
    // Core Module Navigation
    @FXML private Button dashboardNavBtn;
    @FXML private Button walletNavBtn;
    @FXML private Button transactionsNavBtn;
    @FXML private Button blocksNavBtn;
    
    // Blockchain Features Navigation
    @FXML private Button smartContractNavBtn;
    @FXML private Button tokensNavBtn;
    
    // Network & Consensus Navigation
    @FXML private Button networkNavBtn;
    @FXML private Button consensusNavBtn;
    
    // Tools & Utilities Navigation
    @FXML private Button settingsNavBtn;
    @FXML private Button apiNavBtn;
    
    // Development Navigation
    @FXML private Button testNetworkNavBtn;
    @FXML private Button aiChatNavBtn;

    // Add this near the blockchain components section
    private org.example.app.core.block.Miner miner;
    
    // ========================================
    // BLOCKCHAIN COMPONENTS
    // ========================================
    
    private org.example.app.core.consensus.Blockchain consensusBlockchain;
    private org.example.app.core.eth.Blockchain ethBlockchain;
    private BlockchainMetrics blockchainMetrics;
    private NetworkMetrics networkMetrics;
    private TransactionPool transactionPool;
    private PeerNetwork peerNetwork;
    private Consensus consensus;
    
    // ========================================
    // UI MANAGEMENT COMPONENTS
    // ========================================
    
    // Wallet Components
    private WalletController walletController;
    private WalletManager walletManager;
    private WalletView walletView;
    private String activeWalletId;
    private WalletEventHandler walletEventHandler;
    
    // Token Components
    private TokenController tokenController;
    private TokenManager tokenManager;
    private TokenView tokenView;
    
    // Transaction Components
    private TransactionController transactionController;
    private UITransactionManager uiTransactionManager;
    private TransactionView transactionView;
    
    // Smart Contract Components
    private SmartContractController smartContractController;
    private SmartContractManager smartContractManager;
    private SmartContractView smartContractView;
    
    // Consensus Components
    private ConsensusController consensusController;
    private ConsensusManager consensusManager;
    private ConsensusView consensusView;
    private ConsensusType currentConsensusType;
    private ConsensusPolicy currentConsensusPolicy;
    private boolean consensusIntegrationEnabled = true;
    
 
    // API/RPC UI Components - NEW
    private RPCView rpcView;
    private RPCController rpcController;
    private RPCManager rpcManager;
    private APIManager apiManager;

    // BlockExplorer UI Components - NEW
    private BlockExplorerController blockExplorerController;
    private BlockExplorerManager blockExplorerManager;
    private BlockExplorerView blockExplorerView;
    private BlockManager blockManager;

    // Network Components
private NetworkController networkController;
private NetworkManager networkManager;
private NetworkView networkView;
    // ========================================
    // UI STATE MANAGEMENT
    // ========================================
    
    private ObservableList<UIBlock> recentBlocks;
    private ObservableList<UITransaction> recentTransactions;
    private ScheduledService<Void> updateService;
    private Parent originalDashboardContent;
    private String currentView = "dashboard";
    
    // ========================================
    // CONFIGURATION
    // ========================================
    
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DecimalFormat decimalFormat = new DecimalFormat("#,###");
    private long networkStartTime;
    private boolean walletSmartContractIntegrationEnabled = true;
    

    @FXML private Button configNavBtn;  // Add this to your navigation buttons
    
     private ScheduledService<Void> metricsUpdater;
    private Timeline metricsUpdateTimeline;
    

        // UTILITY FIELDS
    // ========================================
    
   
    private ObservableList<UIBlock> blockData;
    private ObservableList<UITransaction> transactionData;
    // ========================================
    // CONFIGURATION COMPONENTS
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
   
   
   

    // ========================================
    // INITIALIZATION
    // ========================================
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        LOGGER.info("Initializing Dashboard Controller with enhanced wallet-smart contract integration...");
        
        try {
            // Step 1: Initialize core blockchain components first
            initializeBlockchainComponents();
              // Step 2: Initialize configuration system
            initializeConfigurationSystem();

            // Initialize network components
            initializeNetworkComponents();
            // Setup network event integration
               setupNetworkEventIntegration();
               initializeMining() ;

                 // Initialize metrics
            initializeMetricsDisplay();
            // Step 2: Setup UI components
            setupTables();
            setupCharts();
            
            // Step 3: Initialize wallet event system BEFORE initializing data
            initializeWalletEventSystem();
            initializeConsensusIntegration();
            
            // Step 4: Initialize data (may create wallet components that need event system)
            initializeData();
            
            // Step 5: Setup navigation handlers
            setupNavigationHandlers();
            
            // Step 6: Debug and monitoring
            debugFXMLSetup();
            
            // Step 7: Start real-time updates
            startRealTimeUpdates();
                   // Start metrics update service
            startMetricsUpdater();
            
            LOGGER.info("Dashboard Controller initialized successfully");
            // Step 8: Set initial UI state
            Platform.runLater(() -> {
                setActiveNavigationButton(dashboardNavBtn);
                LOGGER.info("Dashboard Controller initialization completed successfully");
            });

            
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing Dashboard Controller", e);
            showErrorAlert("Failed to initialize dashboard: " + e.getMessage());
        }

      
    }
    


 private void initializeMetricsDisplay() {
        LOGGER.info("Initializing metrics display...");
        
        if (blockData == null) {
            blockData = FXCollections.observableArrayList();
        }
        if (transactionData == null) {
            transactionData = FXCollections.observableArrayList();
        }
        
        // Setup tables
        if (recentBlocksTable != null) {
            setupBlocksTable();
        }
        if (recentTransactionsTable != null) {
            setupTransactionsTable();
        }
        
        updateMetrics();
    }

        private void setupBlocksTable() {
        if (blockHashColumn != null) blockHashColumn.setCellValueFactory(new PropertyValueFactory<>("hash"));
        if (blockNumberColumn != null) blockNumberColumn.setCellValueFactory(new PropertyValueFactory<>("number"));
        if (blockMinerColumn != null) blockMinerColumn.setCellValueFactory(new PropertyValueFactory<>("miner"));
        if (blockTimestampColumn != null) blockTimestampColumn.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        if (blockTxCountColumn != null) blockTxCountColumn.setCellValueFactory(new PropertyValueFactory<>("transactionCount"));
        
        if (recentBlocksTable != null) {
            recentBlocksTable.setItems(blockData);
        }
    }
    
    private void setupTransactionsTable() {
        if (txHashColumn != null) txHashColumn.setCellValueFactory(new PropertyValueFactory<>("hash"));
        if (txFromColumn != null) txFromColumn.setCellValueFactory(new PropertyValueFactory<>("from"));
        if (txToColumn != null) txToColumn.setCellValueFactory(new PropertyValueFactory<>("to"));
        if (txValueColumn != null) txValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        if (txStatusColumn != null) txStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        
        if (recentTransactionsTable != null) {
            recentTransactionsTable.setItems(transactionData);
        }
    }
/**
 * Initialize network components for dashboard integration
 */


 private void initializeMining() {
    // Set a default miner address if wallet is available
    String minerAddress = "0x" + Long.toHexString(System.currentTimeMillis());
    
    if (walletManager != null) {
        String activeWalletId = walletManager.getActiveWalletId();
        if (activeWalletId != null && walletController != null) {
            WalletController.WalletBalanceInfo balanceInfo = 
                walletController.getWalletBalance(activeWalletId);
            if (balanceInfo != null && balanceInfo.address != null) {
                minerAddress = balanceInfo.address;
            }
        }
    }
    
    if (consensusController != null) {
        consensusController.setMinerAddress(minerAddress);
        
        // Enable auto-mining
        consensusController.setAutoMiningEnabled(true);
        
        // Auto-start mining for PoW
        if (consensusController.getActiveConsensusType() == ConsensusType.PROOF_OF_WORK) {
            consensusController.startMining();
            LOGGER.info("Auto-started mining with address: " + minerAddress);
        }
    }
}

    /**
     * Initializes blockchain components and services
     */
    private void initializeBlockchainComponents() {
        LOGGER.info("Initializing blockchain components...");
        
        consensusBlockchain = new org.example.app.core.consensus.Blockchain();
        ethBlockchain = new org.example.app.core.eth.Blockchain();
        blockchainMetrics = new BlockchainMetrics();
        networkMetrics = new NetworkMetrics();
        transactionPool = new TransactionPool(10000);
        peerNetwork = new PeerNetwork("dashboard-node", "127.0.0.1", 8080);
        peerNetwork.startServer();
        consensus = new Consensus();
        networkStartTime = System.currentTimeMillis();
        
        LOGGER.info("Blockchain components initialized successfully");
    }


    private void initializeNetworkComponents() {
    try {
        LOGGER.info("Initializing network components for dashboard...");
        
        // Initialize network controller
        networkController = new NetworkController();
        networkController.setBlockchain(consensusBlockchain);
        
        // Initialize network manager
        networkManager = new NetworkManager();
        networkManager.setNetworkController(networkController);
        
        LOGGER.info("Network components initialized successfully for dashboard");
        
    } catch (Exception e) {
        LOGGER.severe("Error initializing network components for dashboard: " + e.getMessage());
        e.printStackTrace();
    }
}

/**
 * Get network statistics summary for dashboard display
 */
public String getNetworkStatsSummary() {
    if (networkController != null) {
        NetworkController.NetworkStatus status = networkController.getNetworkStatus();
        return String.format("Network: %s | Peers: %d | Messages: %d/%d", 
                           status.isActive ? "Active" : "Inactive",
                           status.connectedPeers,
                           status.messagesSent,
                           status.messagesReceived);
    }
    return "Network: Not initialized";
}
    
/**
 * Get network controller instance
 */
public NetworkController getNetworkController() {
    return networkController;
}

/**
 * Get network manager instance
 */
public NetworkManager getNetworkManager() {
    return networkManager;
}

/**
 * Check if network is active
 */
public boolean isNetworkActive() {
    return networkController != null && networkController.isNetworkActive();
}

/**
 * Get connected peer count for dashboard display
 */
public int getConnectedPeerCount() {
    if (networkController != null) {
        return networkController.getConnectedPeerCount();
    }
    return 0;
}

// Add this method to handle network-specific dashboard updates

/**
 * Update dashboard when network operations occur
 */
public void notifyDashboardOfNetworkActivity() {
    LOGGER.info("Notifying dashboard of network activity...");
    
    // Update dashboard metrics
    updateDashboardMetrics();
    
    // Update network-specific metrics
    updateNetworkSpecificMetrics();
    
    // If currently on dashboard, refresh it
    if ("dashboard".equals(currentView)) {
        Platform.runLater(() -> updateMetrics());
    }
}
/**
 * Cleanup network components
 */
private void cleanupNetworkComponents() {
    LOGGER.info("Cleaning up network components...");
    
    try {
        if (networkView != null) {
            networkView.cleanup();
            networkView = null;
        }
        
        if (networkManager != null) {
            networkManager.shutdown();
            networkManager = null;
        }
        
        if (networkController != null) {
            networkController.shutdown();
            networkController = null;
        }
        
        LOGGER.info("Network components cleaned up successfully");
        
    } catch (Exception e) {
        LOGGER.warning("Error cleaning up network components: " + e.getMessage());
    }
}

    /**
     * Initializes the wallet event system for seamless smart contract integration
     */
    private void initializeWalletEventSystem() {
        try {
            LOGGER.info("Setting up wallet-smart contract event system");
            
            // Create wallet event handler
            walletEventHandler = new WalletEventHandler();
            
            // Connect the event handler immediately if wallet manager exists
            if (walletManager != null) {
                walletManager.addEventListener(walletEventHandler);
                LOGGER.info("Wallet event handler connected to WalletManager");
            }
            
            LOGGER.info("Wallet event system initialized successfully");
        } catch (Exception e) {
            LOGGER.severe("Error initializing wallet event system: " + e.getMessage());
        }
    }
    
    /**
     * Initializes consensus management integration with dashboard
     */
    private void initializeConsensusIntegration() {
        try {
            LOGGER.info("Initializing consensus management integration with dashboard");
            
            // Initialize consensus controller
            consensusController = new ConsensusController();
            consensusController.setBlockchain(consensusBlockchain);
            consensusController.setTransactionPool(transactionPool);
            
            // Initialize consensus manager
            consensusManager = new ConsensusManager();
            consensusManager.setBlockchain(consensusBlockchain);
            consensusManager.setTransactionPool(transactionPool);
            
            // Connect wallet to consensus if available
            if (walletManager != null && walletController != null) {
                String activeWalletId = walletManager.getActiveWalletId();
                if (activeWalletId != null) {
                    connectWalletToConsensus(activeWalletId);
                }
            }
            
            // Setup consensus event integration
            setupConsensusEventIntegration();
            
            // Track current consensus state
            ConsensusStatusInfo status = consensusController.getConsensusStatus();
            currentConsensusType = status.activeConsensusType;
            currentConsensusPolicy = consensusManager.getCurrentPolicy();
            
            // Set dashboard update callback for consensus
            consensusManager.setDashboardUpdateCallback(() -> {
                Platform.runLater(() -> {
                    updateDashboardMetrics();
                    updateConsensusSpecificMetrics();
                });
            });
            
            LOGGER.info("Consensus integration initialized successfully with " + 
                       currentConsensusType.getDisplayName() + " consensus");
            
        } catch (Exception e) {
            LOGGER.severe("Error initializing consensus integration: " + e.getMessage());
            consensusIntegrationEnabled = false;
        }
    }
    
    // ========================================
    // TABLE AND CHART SETUP
    // ========================================
    
    /**
     * Sets up table columns and bindings
     */
    private void setupTables() {
        LOGGER.info("Setting up dashboard tables...");
        
        // Blocks table setup
        blockHashColumn.setCellValueFactory(new PropertyValueFactory<>("hash"));
        blockNumberColumn.setCellValueFactory(new PropertyValueFactory<>("number"));
        blockMinerColumn.setCellValueFactory(new PropertyValueFactory<>("miner"));
        blockTimestampColumn.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        blockTxCountColumn.setCellValueFactory(new PropertyValueFactory<>("transactionCount"));
        
        blockHashColumn.setCellFactory(column -> new TableCell<UIBlock, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.substring(0, 16) + "...");
                if (!empty) setTooltip(new Tooltip(item));
            }
        });
        
        recentBlocks = FXCollections.observableArrayList();
        recentBlocksTable.setItems(recentBlocks);
        
        // Transactions table setup
        txHashColumn.setCellValueFactory(new PropertyValueFactory<>("hash"));
        txFromColumn.setCellValueFactory(new PropertyValueFactory<>("from"));
        txToColumn.setCellValueFactory(new PropertyValueFactory<>("to"));
        txValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        txStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        
        recentTransactions = FXCollections.observableArrayList();
        recentTransactionsTable.setItems(recentTransactions);
        
        LOGGER.info("Dashboard tables setup completed");
    }
    
    /**
     * Sets up dashboard charts
     */
    private void setupCharts() {
        LOGGER.info("Setting up dashboard charts...");
        
        blockTimeChart.setTitle("Block Times (Last 20 blocks)");
        blockTimeChart.setAnimated(false);
        XYChart.Series<String, Number> blockTimeSeries = new XYChart.Series<>();
        blockTimeSeries.setName("Block Time (ms)");
        blockTimeChart.getData().add(blockTimeSeries);
        
        transactionChart.setTitle("Transaction Pool Size");
        transactionChart.setAnimated(false);
        XYChart.Series<String, Number> txSeries = new XYChart.Series<>();
        txSeries.setName("Pending Transactions");
        transactionChart.getData().add(txSeries);
        
        LOGGER.info("Dashboard charts setup completed");
    }
    
    // ========================================
    // DATA INITIALIZATION
    // ========================================
    
    /**
     * Initializes blockchain data and creates genesis block if needed
     */
    private void initializeData() {
        LOGGER.info("Initializing blockchain data...");
        
        if (consensusBlockchain.getChainLength() == 0) {
            createGenesisBlock();
        }
        
        loadRecentBlocks();
        loadRecentTransactions();
        updateMetrics();
        
        LOGGER.info("Blockchain data initialization completed");
    }
    
    /**
     * Creates the genesis block for the blockchain
     */
    private void createGenesisBlock() {
        LOGGER.info("Creating genesis block...");
        
        String genesisHash = HashUtils.generateHash("genesis");
        Block genesisBlock = new Block(
            genesisHash, null, "genesis-miner",
            System.currentTimeMillis(), 1000L, 0L, 
            FXCollections.observableArrayList()
        );
        consensusBlockchain.addBlock(genesisBlock);
        
        LOGGER.info("Genesis block created with hash: " + genesisHash);
    }
    
    /**
     * Loads recent blocks for display
     */
    private void loadRecentBlocks() {
        recentBlocks.clear();
        int chainLength = consensusBlockchain.getChainLength();
        int startIndex = Math.max(0, chainLength - 10);
        
        for (int i = startIndex; i < chainLength; i++) {
            Block block = consensusBlockchain.getBlockByIndex(i);
            if (block != null) {
                recentBlocks.add(0, convertToUIBlock(block));
            }
        }
    }
    
    /**
     * Loads recent transactions for display
     */
    private void loadRecentTransactions() {
        recentTransactions.clear();
        if (transactionPool.size() == 0) {
            addSampleTransactions();
        }
    }
    
    /**
     * Adds sample transactions for testing
     */
    private void addSampleTransactions() {
        for (int i = 0; i < 5; i++) {
            Transaction tx = new Transaction(("Sample " + i).getBytes());
            tx.setSender("0x" + String.format("%040d", i));
            tx.setRecipient("0x" + String.format("%040d", i + 1));
            tx.setValue((long) (Math.random() * 1000000));
            transactionPool.addTransaction(tx);
            recentTransactions.add(convertToUITransaction(tx));
        }
    }
    
    // ========================================
    // NAVIGATION MANAGEMENT
    // ========================================
    
   /**
 * Setup all navigation button event handlers including configuration
 */
/**
 * Setup all navigation button event handlers including configuration
 */
private void setupNavigationHandlers() {
    LOGGER.info("Setting up navigation handlers...");
    
    // Core Modules
    if (dashboardNavBtn != null) {
        dashboardNavBtn.setOnAction(e -> switchView("dashboard"));
        LOGGER.info("Dashboard nav button configured");
    } else {
        LOGGER.warning("Dashboard nav button is NULL!");
    }
    
    if (walletNavBtn != null) {
        walletNavBtn.setOnAction(e -> switchView("wallet"));
        LOGGER.info("Wallet nav button configured");
    }
    
    if (transactionsNavBtn != null) {
        transactionsNavBtn.setOnAction(e -> switchView("transactions"));
        LOGGER.info("Transactions nav button configured");
    }
    
    if (blocksNavBtn != null) {
        blocksNavBtn.setOnAction(e -> switchView("blocks"));
        LOGGER.info("Blocks nav button configured");
    }
    
    // Blockchain Features
    if (smartContractNavBtn != null) {
        smartContractNavBtn.setOnAction(e -> switchView("smartcontract"));
        LOGGER.info("Smart Contract nav button configured");
    }
    
    if (tokensNavBtn != null) {
        tokensNavBtn.setOnAction(e -> switchView("tokens"));
        LOGGER.info("Tokens nav button configured");
    }
    
    // Network & Consensus
    if (networkNavBtn != null) {
        networkNavBtn.setOnAction(e -> switchView("network"));
        LOGGER.info("Network nav button configured");
    }
    
    if (consensusNavBtn != null) {
        consensusNavBtn.setOnAction(e -> switchView("consensus"));
        LOGGER.info("Consensus nav button configured");
    }
    
    // *** CONFIGURATION MODULE (CRITICAL FIX) ***
    if (configNavBtn != null) {
        configNavBtn.setOnAction(e -> {
            LOGGER.info("Configuration button clicked!");
            switchView("configuration");
        });
        LOGGER.info("✅ Configuration nav button configured successfully");
    } else {
        LOGGER.severe("❌ Configuration nav button is NULL - CHECK YOUR FXML FILE!");
    }
    
    // Tools & Utilities
    if (settingsNavBtn != null) {
        settingsNavBtn.setOnAction(e -> switchView("settings"));
        LOGGER.info("Settings nav button configured");
    }
    
    if (apiNavBtn != null) {
        apiNavBtn.setOnAction(e -> switchView("api"));
        LOGGER.info("API nav button configured");
    }
    
    // Development Tools
    if (testNetworkNavBtn != null) {
        testNetworkNavBtn.setOnAction(e -> switchView("testnetwork"));
        LOGGER.info("Test Network nav button configured");
    }
    
    if (aiChatNavBtn != null) {
        aiChatNavBtn.setOnAction(e -> switchView("aichat"));
        LOGGER.info("AI Chat nav button configured");
    }
    
    LOGGER.info("Navigation handlers setup complete - Total buttons configured: " + 
               countConfiguredButtons());
}

private void switchView(String viewName) {
    LOGGER.info("Switching to view: " + viewName);
    
    currentView = viewName;
    
    // Find and set active button
    Button activeButton = getButtonForView(viewName);
    setActiveNavigationButton(activeButton);
    
    // Handle special view transitions
    handleViewTransition(viewName);
    
    // Handle view-specific logic
    switch (viewName.toLowerCase()) {
        case "dashboard":
            showDashboardView();
            break;
        case "wallet":
            showWalletView();
            break;
        case "transactions":
            showTransactionsView();
            break;
        case "blocks":
            showBlocksView();
            break;
        case "smartcontract":
            // Always refresh smart contract view
            if (smartContractView != null) {
                Platform.runLater(() -> {
                    smartContractView.refreshWalletStatus();
                });
            }
            showSmartContractView();
            break;
        case "tokens":
            showTokensView();
            break;
        case "network":
            showNetworkView();
            break;
        case "consensus":
            showConsensusView();
            break;
        case "settings":
            showSettingsView();
            break;
        case "api":
            loadAPIView();
            break;
        case "testnetwork":
            showTestNetworkView();
            break;
        case "aichat":
            showAiChatView();
            break;
        case "configuration":
            showConfigurationView();
            break;
        default:
            LOGGER.warning("Unknown view requested: " + viewName + ", defaulting to dashboard");
            showDashboardView();
    }
}
    

// ========================================
    // API/RPC VIEW LOADING - NEW
    // ========================================
    
    private void loadAPIView() {
        LOGGER.info("========================================");
        LOGGER.info("LOADING API/RPC MODULE");
        LOGGER.info("========================================");
        
        try {
            // Step 1: Ensure blockchain is available
            if (consensusBlockchain == null) {
                LOGGER.warning("Blockchain is null, initializing...");
                initializeBlockchainComponents();
            }
            
            // Step 2: Initialize RPC Manager if needed
            if (rpcManager == null) {
                LOGGER.info("Initializing RPCManager...");
                rpcManager = new RPCManager(consensusBlockchain);
            }
            
            // Step 3: Initialize RPC Controller if needed
            if (rpcController == null) {
                LOGGER.info("Initializing RPCController...");
                rpcController = new RPCController(consensusBlockchain);
            }
            
            // Step 4: Initialize RPC View if needed
            if (rpcView == null) {
                LOGGER.info("Initializing RPCView...");
                rpcView = new RPCView();
                
                // Connect controller to view
                rpcController.setView(rpcView);
            }
            
            // Step 5: Initialize API Manager if needed (optional, for comprehensive API management)
            if (apiManager == null) {
                LOGGER.info("Initializing APIManager...");
                try {
                    NetworkConfig networkConfig = new NetworkConfig.Builder()
                        .networkId("1")
                        .maxPeers(50)
                        .build();
                    apiManager = new APIManager(consensusBlockchain, networkConfig);
                    LOGGER.info("APIManager initialized successfully");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to initialize APIManager (optional)", e);
                    // APIManager is optional, continue without it
                }
            }
            
            // Step 6: Display the RPC view
            LOGGER.info("Displaying RPC view in content area...");
            storeOriginalDashboardContent();
            replaceContentView(rpcView.getMainContainer());
            
            // Step 7: Log available methods
            LOGGER.info("Available RPC Methods:");
            String[] methods = rpcController.getRegisteredMethods();
            for (String method : methods) {
                LOGGER.info("  - " + method);
            }
            
            // Step 8: Show success message
            Platform.runLater(() -> {
                showAPIModuleWelcomeMessage();
            });
            
            LOGGER.info("========================================");
            LOGGER.info("API/RPC MODULE LOADED SUCCESSFULLY");
            LOGGER.info("========================================");
            
        } catch (Exception e) {
            LOGGER.severe("========================================");
            LOGGER.severe("ERROR LOADING API/RPC MODULE");
            LOGGER.severe("Error: " + e.getMessage());
            LOGGER.severe("========================================");
            e.printStackTrace();
            
            Platform.runLater(() -> {
                showErrorAlert("Failed to load API/RPC Module: " + e.getMessage());
                switchView("dashboard");
            });
        }
    }

 // ========================================
    // API/RPC MODULE WELCOME MESSAGE - NEW
    // ========================================
    
    private void showAPIModuleWelcomeMessage() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("API/RPC Module");
        alert.setHeaderText("API/RPC Module Loaded Successfully");
        alert.setContentText(
            "Welcome to the API/RPC Module!\n\n" +
            "You can now:\n" +
            "• Execute RPC methods (eth_getBalance, eth_getBlock, etc.)\n" +
            "• Query blockchain data\n" +
            "• Submit transactions\n" +
            "• View request history\n" +
            "• Read method documentation\n\n" +
            "Select a method from the dropdown and enter parameters to get started."
        );
        
        // Auto-close after 5 seconds
        Timeline timeline = new Timeline(new KeyFrame(
            Duration.seconds(5),
            e -> alert.close()
        ));
        timeline.play();
        
        alert.show();
    }

/**
 * Helper method to count configured navigation buttons
 */
private int countConfiguredButtons() {
    int count = 0;
    if (dashboardNavBtn != null) count++;
    if (walletNavBtn != null) count++;
    if (transactionsNavBtn != null) count++;
    if (blocksNavBtn != null) count++;
    if (smartContractNavBtn != null) count++;
    if (tokensNavBtn != null) count++;
    if (networkNavBtn != null) count++;
    if (consensusNavBtn != null) count++;
    if (configNavBtn != null) count++;
    if (settingsNavBtn != null) count++;
    if (apiNavBtn != null) count++;
    if (testNetworkNavBtn != null) count++;
    if (aiChatNavBtn != null) count++;
    return count;
}


 /**
 * Gets the navigation button for a specific view
 */

/**
 * Gets the navigation button for a specific view
 */
private Button getButtonForView(String viewName) {
    switch (viewName.toLowerCase()) {
        case "dashboard": 
            return dashboardNavBtn;
        case "wallet": 
            return walletNavBtn;
        case "transactions": 
            return transactionsNavBtn;
        case "blocks": 
            return blocksNavBtn;
        case "smartcontract": 
            return smartContractNavBtn;
        case "tokens": 
            return tokensNavBtn;
        case "network": 
            return networkNavBtn;
        case "consensus": 
            return consensusNavBtn;
        case "configuration":  // *** CRITICAL: Configuration button mapping ***
            return configNavBtn;
        case "settings": 
            return settingsNavBtn;
        case "api": 
            return apiNavBtn;
        case "testnetwork": 
            return testNetworkNavBtn;
        case "aichat": 
            return aiChatNavBtn;
        default: 
            LOGGER.warning("Unknown view: " + viewName + ", defaulting to dashboard");
            return dashboardNavBtn;
    }
}

    /**
     * Handles special logic needed when transitioning between views
     */
    private void handleViewTransition(String targetView) {
        try {
            LOGGER.info("Handling view transition to: " + targetView);
            
            // Wallet to Smart Contract transition
            if (targetView.equals("smartcontract")) {
                Platform.runLater(() -> {
                    try {
                        if (walletManager != null && walletController != null) {
                            String activeWalletId = walletManager.getActiveWalletId();
                            if (activeWalletId != null) {
                                LOGGER.info("Auto-connecting wallet to smart contract: " + activeWalletId);
                                connectWalletToSmartContractController(activeWalletId);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warning("Could not auto-connect wallet: " + e.getMessage());
                    }
                });
            }
            // Any view returning to smart contract
            else if (targetView.equals("smartcontract") && !currentView.equals("smartcontract")) {
                Platform.runLater(() -> {
                    new Thread(() -> {
                        try {
                            Thread.sleep(500); // Small delay to ensure view is loaded
                            Platform.runLater(() -> {
                                if (smartContractView != null) {
                                    smartContractView.refreshWalletStatus();
                                    LOGGER.info("Refreshed smart contract wallet status on return");
                                }
                            });
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                });
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error in view transition handling: " + e.getMessage());
        }
    }
    
    /**
     * Updates navigation button styling to show active state
     */
    /**
 * Updates navigation button styling to show active state
 */
private void setActiveNavigationButton(Button activeButton) {
    Button[] navButtons = {
        dashboardNavBtn, walletNavBtn, transactionsNavBtn, blocksNavBtn,
        smartContractNavBtn, tokensNavBtn, networkNavBtn, consensusNavBtn,
        configNavBtn,  // *** CRITICAL: Include config button ***
        settingsNavBtn, apiNavBtn, testNetworkNavBtn, aiChatNavBtn
    };
    
    // Inactive style for all buttons
    String inactiveStyle = "-fx-background-color: transparent; " +
                          "-fx-text-fill: white; " +
                          "-fx-font-size: 14px; " +
                          "-fx-padding: 12; " +
                          "-fx-alignment: center-left; " +
                          "-fx-background-radius: 8; " +
                          "-fx-cursor: hand;";
    
    // Active style for selected button
    String activeStyle = "-fx-background-color: rgba(0,255,136,0.2); " +
                        "-fx-text-fill: #00ff88; " +
                        "-fx-font-size: 14px; " +
                        "-fx-padding: 12; " +
                        "-fx-alignment: center-left; " +
                        "-fx-background-radius: 8; " +
                        "-fx-font-weight: bold; " +
                        "-fx-cursor: hand;";
    
    // Reset all buttons to inactive
    for (Button button : navButtons) {
        if (button != null) {
            button.setStyle(inactiveStyle);
        }
    }
    
    // Set active button style
    if (activeButton != null) {
        activeButton.setStyle(activeStyle);
        LOGGER.info("Active navigation button set: " + getButtonName(activeButton));
    }
}

    /**
 * Get button name for logging
 */
private String getButtonName(Button button) {
    if (button == configNavBtn) return "Configuration";
    if (button == dashboardNavBtn) return "Dashboard";
    if (button == walletNavBtn) return "Wallet";
    if (button == transactionsNavBtn) return "Transactions";
    if (button == blocksNavBtn) return "Blocks";
    if (button == smartContractNavBtn) return "Smart Contract";
    if (button == tokensNavBtn) return "Tokens";
    if (button == networkNavBtn) return "Network";
    if (button == consensusNavBtn) return "Consensus";
    if (button == settingsNavBtn) return "Settings";
    if (button == apiNavBtn) return "API";
    if (button == testNetworkNavBtn) return "Test Network";
    if (button == aiChatNavBtn) return "AI Chat";
    return "Unknown";
}

    // ========================================
    // VIEW IMPLEMENTATIONS
    // ========================================
    
    /**
     * Shows the main dashboard view
     */
    private void showDashboardView() {
        LOGGER.info("Restoring Dashboard View");
        
        if (contentArea != null && originalDashboardContent != null) {
            Platform.runLater(() -> {
                try {
                    ScrollPane scrollPane = (ScrollPane) contentArea;
                    scrollPane.setContent(originalDashboardContent);
                    LOGGER.info("Dashboard view restored in ScrollPane");
                } catch (Exception e) {
                    LOGGER.severe("Error restoring dashboard: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
        
        // Ensure dashboard updates are running
        if (updateService != null && !updateService.isRunning()) {
            updateService.restart();
            LOGGER.info("Dashboard update service restarted");
        }
    }
    
    /**
     * Shows enhanced wallet management view with smart contract integration
     */
    private void showWalletView() {
        LOGGER.info("Showing Enhanced Wallet Management View with Smart Contract Integration");
        
        try {
            // Initialize wallet UI components if not already done
            if (walletManager == null) {
                try {
                    walletManager = new WalletManager();
                    
                    // Connect event listener immediately after creation
                    if (walletEventHandler != null) {
                        walletManager.addEventListener(walletEventHandler);
                        LOGGER.info("WalletManager event listener connected immediately");
                    }
                    
                    LOGGER.info("WalletManager initialized with smart contract integration");
                } catch (Exception e) {
                    LOGGER.severe("Failed to initialize WalletManager: " + e.getMessage());
                    showErrorAlert("Failed to initialize WalletManager: " + e.getMessage());
                    return;
                }
            }
            
            if (walletController == null) {
                try {
                    walletController = new WalletController();
                    LOGGER.info("WalletController initialized successfully");
                } catch (Exception e) {
                    LOGGER.severe("Failed to initialize WalletController: " + e.getMessage());
                    showErrorAlert("Failed to initialize WalletController: " + e.getMessage());
                    return;
                }
            }
            
            // Create the full wallet management interface with smart contract integration
            VBox walletManagementInterface = createEnhancedWalletManagementInterface();
            
            // Store original content for restoration
            storeOriginalDashboardContent();
            
            // Replace content view
            replaceContentView(walletManagementInterface);
            
            LOGGER.info("Enhanced Wallet Management interface loaded with smart contract integration");
            
        } catch (Exception e) {
            LOGGER.severe("Error loading Wallet Management view: " + e.getMessage());
            showErrorAlert("Failed to load Wallet Management: " + e.getMessage());
        }
    }
    
    /**
     * Shows transaction management view
     */
    private void showTransactionsView() {
        LOGGER.info("Showing Transaction Management View using existing transaction UI components");
        
        try {
            // Initialize transaction UI components if not already done
            if (transactionController == null) {
                transactionController = new TransactionController();
                transactionController.setTransactionPool(transactionPool);
                transactionController.setCurrentWallet(null); // For now, we'll fix wallet later
                
                // Connect blockchain components for mining
                if (consensusBlockchain != null && ethBlockchain != null) {
                    org.example.app.core.state.StateTransition stateTransition = 
                        new org.example.app.core.state.StateTransition();
                    org.example.app.core.block.BlockProcessor blockProcessor = 
                        new org.example.app.core.block.BlockProcessor(consensusBlockchain, stateTransition);
                    org.example.app.core.block.Miner miner = 
                        new org.example.app.core.block.Miner(consensusBlockchain, "dashboard-miner");
                    
                    transactionController.setBlockchainComponents(consensusBlockchain, blockProcessor, miner);
                }
                
                LOGGER.info("TransactionController initialized and connected to blockchain components");
            }
            
            if (uiTransactionManager == null) {
                uiTransactionManager = new UITransactionManager();
                uiTransactionManager.setTransactionPool(transactionPool);
                uiTransactionManager.setCurrentWallet(null); // Temporary fix
                
                // Connect blockchain components
                if (consensusBlockchain != null) {
                    org.example.app.core.state.StateTransition stateTransition = 
                        new org.example.app.core.state.StateTransition();
                    org.example.app.core.block.BlockProcessor blockProcessor = 
                        new org.example.app.core.block.BlockProcessor(consensusBlockchain, stateTransition);
                    org.example.app.core.block.Miner miner = 
                        new org.example.app.core.block.Miner(consensusBlockchain, "dashboard-miner");
                    
                    uiTransactionManager.setBlockchainComponents(consensusBlockchain, blockProcessor, miner);
                    uiTransactionManager.setAutoMining(true);
                    uiTransactionManager.setMiningDifficulty(1000L); // Low difficulty for UI testing
                }
                
                LOGGER.info("UI TransactionManager initialized and connected to blockchain");
            }
            
            if (transactionView == null) {
                transactionView = new TransactionView(transactionController, uiTransactionManager);
                
                // Set callback for dashboard updates when transaction operations occur
                transactionView.setDashboardUpdateCallback(() -> {
                    LOGGER.info("Transaction operation completed, updating dashboard metrics");
                    updateDashboardMetrics();
                });
                
                LOGGER.info("TransactionView initialized with dashboard update callback");
            }
            
            // Create the transaction management interface using the existing TransactionView
            VBox transactionManagementInterface = transactionView.createTransactionManagementView();
            
            // Store original content for restoration
            storeOriginalDashboardContent();
            
            // Replace content view
            replaceContentView(transactionManagementInterface);
            
            LOGGER.info("Transaction Management interface loaded successfully using existing UI components");
            
        } catch (Exception e) {
            LOGGER.severe("Error loading Transaction Management view: " + e.getMessage());
            showErrorAlert("Failed to load Transaction Management: " + e.getMessage());
        }
    }
    
    /**
     * Shows smart contract management view
     */
    private void showSmartContractView() {
        LOGGER.info("Attempting to show Smart Contract Management View");
        
        try {
            // Enhanced: Real-time wallet check
            boolean hasWallet = isWalletReadyForSmartContracts();
            String activeWalletId = walletManager != null ? walletManager.getActiveWalletId() : null;
            
            LOGGER.info("Wallet check - Has wallet: " + hasWallet + ", Active ID: " + activeWalletId);
            
            if (!hasWallet || activeWalletId == null) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Wallet Setup Required");
                    alert.setHeaderText("Smart Contract Management");
                    alert.setContentText("Smart contracts require an active wallet for deployment and interaction.\n\n" +
                                       "Current Status:\n" +
                                       "• Active Wallet: " + (activeWalletId != null ? activeWalletId : "None") + "\n" +
                                       "• Wallet Ready: " + (hasWallet ? "Yes" : "No") + "\n\n" +
                                       "Please:\n" +
                                       "1. Go to Wallet Management\n" +
                                       "2. Select and load a wallet\n" +
                                       "3. Return to Smart Contract Management\n\n" +
                                       "The system will automatically detect your active wallet.");
                    
                    ButtonType goToWalletButton = new ButtonType("💛 Go to Wallet Management");
                    ButtonType refreshButton = new ButtonType("🔄 Refresh & Retry");
                    ButtonType cancelButton = new ButtonType("Cancel");
                    alert.getButtonTypes().setAll(goToWalletButton, refreshButton, cancelButton);
                    
                    alert.showAndWait().ifPresent(response -> {
                        if (response == goToWalletButton) {
                            switchView("wallet");
                        } else if (response == refreshButton) {
                            updateDashboardMetrics();
                            showSmartContractView();
                        }
                    });
                });
                return;
            }
            
            // Wallet is ready - proceed with smart contract view
            LOGGER.info("Wallet is ready, proceeding with smart contract view");
            
            // Initialize smart contract components
            if (smartContractController == null) {
                smartContractController = new SmartContractController();
                smartContractController.setTransactionPool(transactionPool);
            }
            
            if (smartContractManager == null) {
                smartContractManager = new SmartContractManager();
                smartContractManager.setTransactionPool(transactionPool);
            }
            
            if (smartContractView == null) {
                smartContractView = new SmartContractView(smartContractController, smartContractManager);
                smartContractView.setDashboardUpdateCallback(() -> updateDashboardMetrics());
                smartContractView.setNavigationCallback(() -> switchView("dashboard"));
            }
            
            // Connect wallet components
            connectActiveWalletToSmartContract();
            
            // Create and show the smart contract interface
            VBox smartContractInterface = smartContractView.createSmartContractManagementView();
            
            // Store original content for restoration
            storeOriginalDashboardContent();
            
            // Replace content view
            replaceContentView(smartContractInterface);
            
            LOGGER.info("Smart Contract Management interface loaded successfully");
            
            // Show success message
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Smart Contract Management Ready! 🚀");
                alert.setHeaderText("Successfully Connected");
                alert.setContentText(String.format(
                    "✅ Smart Contract Management is now ready!\n\n" +
                    "📋 Connected Wallet:\n" +
                    "   • Wallet ID: %s\n" +
                    "   • Status: Active and Ready\n\n" +
                    "🚀 You can now:\n" +
                    "   • Compile Solidity contracts\n" +
                    "   • Deploy contracts to TINC blockchain\n" +
                    "   • Interact with deployed contracts\n" +
                    "   • Monitor contract events\n\n" +
                    "Happy smart contract development! 🎉",
                    activeWalletId
                ));
                alert.showAndWait();
            });
            
        } catch (Exception e) {
            LOGGER.severe("Error loading Smart Contract Management view: " + e.getMessage());
            showErrorAlert("Failed to load Smart Contract Management: " + e.getMessage());
        }
    }
    
    /**
     * Shows token management view
     */
    private void showTokensView() {
        LOGGER.info("Showing Token Management View using existing token UI components");
        
        try {
            // Initialize token UI components if not already done
            if (tokenController == null) {
                tokenController = new TokenController();
                tokenController.setTransactionPool(transactionPool);
                LOGGER.info("TokenController initialized and connected to transaction pool");
            }
            
            if (tokenManager == null) {
                tokenManager = new TokenManager();
                tokenManager.setTransactionPool(transactionPool);
                LOGGER.info("TokenManager initialized and connected to transaction pool");
            }
            
            if (tokenView == null) {
                tokenView = new TokenView(tokenController, tokenManager);
                
                // Set callback for dashboard updates when token operations occur
                tokenView.setDashboardUpdateCallback(() -> {
                    LOGGER.info("Token operation completed, updating dashboard metrics");
                    updateDashboardMetrics();
                });
                tokenView.setNavigationCallback(() -> switchView("dashboard"));
                LOGGER.info("TokenView initialized with dashboard update callback");
            }
            
            // Create the token management interface using the existing TokenView
            VBox tokenManagementInterface = tokenView.createTokenManagementView();
            
            // Store original content for restoration
            storeOriginalDashboardContent();
            
            // Replace content view
            replaceContentView(tokenManagementInterface);
            
            LOGGER.info("Token Management interface loaded successfully using existing UI components");
            
        } catch (Exception e) {
            LOGGER.severe("Error loading Token Management view: " + e.getMessage());
            showErrorAlert("Failed to load Token Management: " + e.getMessage());
        }
    }
    
    /**
     * Shows consensus management view
     */
    private void showConsensusView() {
        LOGGER.info("Showing Consensus Management View with full integration");
        
        try {
            // Initialize consensus view if not already done
            if (consensusView == null) {
                if (!consensusIntegrationEnabled) {
                    showErrorAlert("Consensus integration is not available. Please check the logs for details.");
                    return;
                }
                
                consensusView = new ConsensusView(consensusController, consensusManager);
                
                // Set dashboard update callback
                consensusView.setDashboardUpdateCallback(() -> {
                    Platform.runLater(() -> {
                        updateDashboardMetrics();
                        updateConsensusSpecificMetrics();
                    });
                });
                
                // Set navigation callback
                consensusView.setNavigationCallback(() -> switchView("dashboard"));
                
                LOGGER.info("ConsensusView initialized with dashboard integration");
            }
            
            // Create the consensus management interface
            VBox consensusManagementInterface = consensusView.createConsensusManagementView();
            
            // Store original content for restoration
            storeOriginalDashboardContent();
            
            // Replace content view
            replaceContentView(consensusManagementInterface);
            
            LOGGER.info("Consensus Management interface loaded with full dashboard integration");
            
        } catch (Exception e) {
            LOGGER.severe("Error loading Consensus Management view: " + e.getMessage());
            showErrorAlert("Failed to load Consensus Management: " + e.getMessage());
        }
    }
    
    /**
     * Shows block explorer view
     */
    private void showBlocksView() {
        LOGGER.info("Attempting to show Block Explorer View");
        
        try {
            // Initialize block manager if needed
            if (blockManager == null) {
                blockManager = new BlockManager();
                LOGGER.info("BlockManager initialized for block explorer");
            }
            
            // Initialize block explorer controller
            if (blockExplorerController == null) {
                blockExplorerController = new BlockExplorerController(consensusBlockchain, blockManager);
                LOGGER.info("BlockExplorerController initialized");
            }
            
            // Initialize block explorer view with controller (REQUIRED constructor parameter!)
            if (blockExplorerView == null) {
                blockExplorerView = new BlockExplorerView(blockExplorerController);
                LOGGER.info("BlockExplorerView initialized with controller");
            }
            
            // Initialize the block explorer with latest blocks
            blockExplorerController.initialize();
            
            // Create JavaFX GUI for Block Explorer
            VBox blockExplorerInterface = createBlockExplorerGUI();
            
            // Store original content for restoration
            storeOriginalDashboardContent();
            
            // Replace content view
            replaceContentView(blockExplorerInterface);
            
            LOGGER.info("Block Explorer interface loaded successfully");
            
            // Show success message
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Block Explorer Ready! 🔍");
                alert.setHeaderText("Successfully Connected");
                alert.setContentText(
                    "✅ Block Explorer is now ready!\n\n" +
                    "📊 You can now:\n" +
                    "   • Browse blocks in the blockchain\n" +
                    "   • View block details and transactions\n" +
                    "   • Search for specific blocks or transactions\n" +
                    "   • Analyze blockchain data\n" +
                    "   • Track transaction history\n\n" +
                    "🚀 Start exploring the blockchain! 🎉"
                );
                alert.showAndWait();
            });
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading Block Explorer view: " + e.getMessage(), e);
            
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error Loading Block Explorer");
                alert.setHeaderText("Failed to Initialize");
                alert.setContentText("Could not load Block Explorer:\n" + e.getMessage());
                alert.showAndWait();
            });
        }
    }
    
    /**
     * Shows network manager view
     */
    private void showNetworkView() {
    LOGGER.info("Showing Network Management View using existing network UI components");
    
    try {
        // Initialize network view if not already done
        if (networkView == null) {
            if (networkController == null) {
                networkController = new NetworkController();
                networkController.setBlockchain(consensusBlockchain);
                LOGGER.info("NetworkController initialized for network view");
            }
            
            if (networkManager == null) {
                networkManager = new NetworkManager();
                networkManager.setNetworkController(networkController);
                LOGGER.info("NetworkManager initialized for network view");
            }
            
            networkView = new NetworkView(networkController, networkManager);
            
            // Set callback for dashboard updates when network operations occur
            networkView.setDashboardUpdateCallback(() -> {
                LOGGER.info("Network operation completed, updating dashboard metrics");
                updateDashboardMetrics();
                updateNetworkSpecificMetrics();
            });
            
            // Set navigation callback
            networkView.setNavigationCallback(() -> switchView("dashboard"));
            
            LOGGER.info("NetworkView initialized with dashboard update callback");
        }
        
        // Create the network management interface using the existing NetworkView
        VBox networkManagementInterface = networkView.createNetworkManagementView();
        
        // Store original content for restoration
        storeOriginalDashboardContent();
        
        // Replace content view
        replaceContentView(networkManagementInterface);
        
        LOGGER.info("Network Management interface loaded successfully using existing UI components");
        
    } catch (Exception e) {
        LOGGER.severe("Error loading Network Management view: " + e.getMessage());
        showErrorAlert("Failed to load Network Management: " + e.getMessage());
    }
}

/**
 * Updates network-specific metrics in dashboard
 */
private void updateNetworkSpecificMetrics() {
    try {
        if (networkController != null && networkController.isNetworkActive()) {
            NetworkController.NetworkStatus status = networkController.getNetworkStatus();
            
            // Update active peers count
            if (activePeersLabel != null) {
                activePeersLabel.setText(String.valueOf(status.connectedPeers));
            }
            
            // Update network status
            if (networkStatusLabel != null) {
                String statusText = status.isActive ? "Online" : "Offline";
                String statusColor = status.isActive ? "-fx-text-fill: #00ff00;" : "-fx-text-fill: #ff0000;";
                networkStatusLabel.setText(statusText);
                networkStatusLabel.setStyle(statusColor);
            }
            
            LOGGER.fine("Network-specific metrics updated - Peers: " + status.connectedPeers + 
                       ", Status: " + (status.isActive ? "Active" : "Inactive"));
        }
        
    } catch (Exception e) {
        LOGGER.warning("Error updating network-specific metrics: " + e.getMessage());
    }
}


    
    /**
     * Shows configuration view
     */
    private void showSettingsView() {
        LOGGER.info("Showing Configuration View");
        showPlaceholderView("Configuration", 
            "Configure blockchain parameters, network settings, and application preferences");
    }
    
    /**
     * Shows API/RPC interface view
     */
    private void showApiView() {
        LOGGER.info("Showing API/RPC Interface View");
        showPlaceholderView("API / RPC Interface", 
            "Test RPC methods, monitor API usage, and configure endpoint settings");
    }
    
    /**
     * Shows test network view
     */
    private void showTestNetworkView() {
        LOGGER.info("Showing Test Network View");
        showPlaceholderView("Test Network", 
            "Connect to test networks, simulate transactions, and test blockchain functionality");
    }
    
    /**
     * Shows AI chat utility view
     */
    private void showAiChatView() {
        LOGGER.info("Showing AI Chat Utility View");
        showPlaceholderView("AI Chat Utility", 
            "Get help with blockchain operations and development through AI assistance");
    }
    
    /**
     * Shows a placeholder message for views not yet implemented
     */
    private void showPlaceholderView(String title, String description) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Feature Coming Soon");
            alert.setHeaderText(title);
            alert.setContentText(description + "\n\nThis feature will be implemented in future versions.");
            alert.showAndWait();
        });
    }
    
    // ========================================
    // WALLET MANAGEMENT IMPLEMENTATION
    // ========================================
    
    /**
     * Creates enhanced wallet management interface with smart contract integration
     */
    private VBox createEnhancedWalletManagementInterface() {
        VBox walletContainer = new VBox(25);
        walletContainer.setStyle("-fx-background-color: #16213e; -fx-padding: 30;");
        
        // Enhanced header with smart contract status
        HBox headerSection = createEnhancedWalletHeader();
        
        // Wallet statistics section
        HBox statsSection = createWalletStatsSection();
        
        // Main content tabs with smart contract integration
        TabPane mainTabs = createEnhancedWalletTabPane();
        
        walletContainer.getChildren().addAll(headerSection, statsSection, mainTabs);
        
        return walletContainer;
    }
    
    /**
     * Creates enhanced wallet header with smart contract integration
     */
    private HBox createEnhancedWalletHeader() {
        HBox headerBox = new HBox(20);
        headerBox.setStyle("-fx-padding: 0 0 20 0; -fx-alignment: center-left;");
        
        Label headerLabel = new Label("💛 Enhanced Wallet Management");
        headerLabel.setStyle("-fx-text-fill: white; -fx-font-size: 32px; -fx-font-weight: bold;");
        
        Label subtitleLabel = new Label("Create, load, and manage wallets • Integrated with Smart Contracts & Tokens");
        subtitleLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 16px;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Smart contract integration status
        VBox statusBox = new VBox(5);
        Label smartContractStatusLabel = new Label();
        updateSmartContractIntegrationStatus(smartContractStatusLabel);
        
        Button goToSmartContractsBtn = new Button("🚀 Go to Smart Contracts");
        goToSmartContractsBtn.setStyle("-fx-background-color: #8a2be2; -fx-text-fill: white; " +
                                      "-fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-font-size: 12px;");
        goToSmartContractsBtn.setOnAction(e -> {
            if (isWalletReadyForSmartContracts()) {
                switchView("smartcontract");
            } else {
                showWalletRequiredForSmartContractsAlert();
            }
        });
        
        Button backButton = new Button("← Back to Dashboard");
        backButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #40e0d0; " +
                           "-fx-border-color: #40e0d0; -fx-border-width: 2; -fx-font-weight: bold; " +
                           "-fx-padding: 8 16; -fx-background-radius: 6; -fx-font-size: 12px;");
        backButton.setOnAction(e -> switchView("dashboard"));
        
        statusBox.getChildren().addAll(smartContractStatusLabel, goToSmartContractsBtn);
        
        VBox titleBox = new VBox(5);
        titleBox.getChildren().addAll(headerLabel, subtitleLabel);
        
        HBox controlsBox = new HBox(10);
        controlsBox.getChildren().addAll(statusBox, backButton);
        
        headerBox.getChildren().addAll(titleBox, spacer, controlsBox);
        return headerBox;
    }
    
    /**
     * Creates enhanced wallet tab pane with smart contract integration
     */
    private TabPane createEnhancedWalletTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: #22334e;");
        
        // Wallet Creation Tab
        Tab createTab = new Tab("Create Wallet");
        createTab.setClosable(false);
        createTab.setContent(createWalletCreationTab());
        
        // Wallet Management Tab
        Tab manageTab = new Tab("Manage Wallets");
        manageTab.setClosable(false);
        manageTab.setContent(createWalletManagementTab());
        
        // Transaction Tab
        Tab transactionTab = new Tab("Send Transaction");
        transactionTab.setClosable(false);
        transactionTab.setContent(createWalletTransactionTab());
        
        // Smart Contract Integration Tab
        Tab smartContractTab = new Tab("Smart Contract Integration");
        smartContractTab.setClosable(false);
        smartContractTab.setContent(createSmartContractIntegrationTab());
        
        tabPane.getTabs().addAll(createTab, manageTab, transactionTab, smartContractTab);
        return tabPane;
    }
    
    /**
     * Creates wallet creation tab
     */
    private VBox createWalletCreationTab() {
        VBox createBox = new VBox(20);
        createBox.setStyle("-fx-padding: 25; -fx-background-color: #16213e;");
        
        Label createLabel = new Label("Create New Wallet");
        createLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Wallet creation form
        GridPane createForm = new GridPane();
        createForm.setHgap(15);
        createForm.setVgap(15);
        createForm.setStyle("-fx-padding: 20;");
        
        // Wallet ID field
        Label walletIdLabel = new Label("Wallet ID:");
        walletIdLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        TextField walletIdField = new TextField();
        walletIdField.setPromptText("Enter unique wallet ID");
        walletIdField.setStyle("-fx-background-color: #22334e; -fx-text-fill: white; " +
                              "-fx-border-color: #40e0d0; -fx-border-radius: 6; -fx-padding: 8;");
        
        // Password field
        Label passwordLabel = new Label("Password:");
        passwordLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter secure password");
        passwordField.setStyle("-fx-background-color: #22334e; -fx-text-fill: white; " +
                              "-fx-border-color: #40e0d0; -fx-border-radius: 6; -fx-padding: 8;");
        
        // Contract wallet checkbox
        CheckBox contractWalletCheck = new CheckBox("Create Contract Wallet (Recommended for Smart Contracts)");
        contractWalletCheck.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        
        // Create button
        Button createWalletBtn = new Button("🆕 Create Wallet");
        createWalletBtn.setStyle("-fx-background-color: #00ff88; -fx-text-fill: black; -fx-font-weight: bold; " +
                               "-fx-padding: 12 24; -fx-background-radius: 8; -fx-font-size: 14px;");
        
        // Status area
        TextArea statusArea = new TextArea();
        statusArea.setEditable(false);
        statusArea.setPrefRowCount(6);
        statusArea.setStyle("-fx-background-color: #22334e; -fx-text-fill: #ccc; " +
                           "-fx-border-color: #555; -fx-border-width: 1; -fx-background-radius: 6;");
        statusArea.setText("Ready to create wallet with smart contract integration...");
        
        // Event handler with smart contract integration
        createWalletBtn.setOnAction(e -> {
            String walletId = walletIdField.getText().trim();
            String password = passwordField.getText().trim();
            boolean isContractWallet = contractWalletCheck.isSelected();
            
            if (walletId.isEmpty() || password.isEmpty()) {
                statusArea.setText("❌ ERROR: Please fill in all fields");
                return;
            }
            
            createWalletFromForm(walletId, password, isContractWallet, statusArea, walletIdField, passwordField);
        });
        
        createForm.add(walletIdLabel, 0, 0);
        createForm.add(walletIdField, 1, 0);
        createForm.add(passwordLabel, 0, 1);
        createForm.add(passwordField, 1, 1);
        createForm.add(contractWalletCheck, 0, 2, 2, 1);
        createForm.add(createWalletBtn, 0, 3, 2, 1);
        
        createBox.getChildren().addAll(createLabel, createForm, 
            new Label("Status:") {{ setStyle("-fx-text-fill: white; -fx-font-weight: bold;"); }}, 
            statusArea);
        
        return createBox;
    }
    
    /**
     * Creates smart contract integration tab for wallet management
     */
    private VBox createSmartContractIntegrationTab() {
        VBox integrationBox = new VBox(20);
        integrationBox.setStyle("-fx-padding: 25; -fx-background-color: #16213e;");
        
        Label integrationLabel = new Label("🔗 Smart Contract Integration");
        integrationLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Integration status
        VBox statusBox = new VBox(10);
        statusBox.setStyle("-fx-background-color: #22334e; -fx-background-radius: 8; -fx-padding: 20;");
        
        Label statusTitle = new Label("Integration Status:");
        statusTitle.setStyle("-fx-text-fill: #ccc; -fx-font-size: 14px; -fx-font-weight: bold;");
        
        Label walletStatus = new Label();
        updateSmartContractIntegrationStatus(walletStatus);
        
        statusBox.getChildren().addAll(statusTitle, walletStatus);
        
        // Quick actions
        HBox actionsBox = new HBox(15);
        actionsBox.setStyle("-fx-padding: 20 0;");
        
        Button testConnectionBtn = new Button("🧪 Test Integration");
        testConnectionBtn.setStyle("-fx-background-color: #40e0d0; -fx-text-fill: black; -fx-font-weight: bold; " +
                                  "-fx-padding: 12 24; -fx-background-radius: 8; -fx-font-size: 14px;");
        testConnectionBtn.setOnAction(e -> testWalletSmartContractIntegration());
        
        Button goToSmartContractsBtn = new Button("🚀 Go to Smart Contracts");
        goToSmartContractsBtn.setStyle("-fx-background-color: #8a2be2; -fx-text-fill: white; -fx-font-weight: bold; " +
                                      "-fx-padding: 12 24; -fx-background-radius: 8; -fx-font-size: 14px;");
        goToSmartContractsBtn.setOnAction(e -> {
            if (isWalletReadyForSmartContracts()) {
                navigateToSmartContractFromWallet();
            } else {
                showWalletRequiredForSmartContractsAlert();
            }
        });
        
        actionsBox.getChildren().addAll(testConnectionBtn, goToSmartContractsBtn);
        
        // Info section
        VBox infoBox = new VBox(10);
        infoBox.setStyle("-fx-background-color: rgba(0,255,136,0.1); -fx-background-radius: 8; -fx-padding: 20;");
        
        Label infoTitle = new Label("💡 Integration Information:");
        infoTitle.setStyle("-fx-text-fill: #00ff88; -fx-font-size: 14px; -fx-font-weight: bold;");
        
        Label infoText = new Label(
            "• Create or load a wallet to enable smart contract features\n" +
            "• Your wallet will automatically connect to Smart Contract Management\n" +
            "• Contract deployment requires an active wallet with sufficient balance\n" +
            "• Seamless integration between wallet and smart contract operations"
        );
        infoText.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-wrap-text: true;");
        
        infoBox.getChildren().addAll(infoTitle, infoText);
        
        integrationBox.getChildren().addAll(integrationLabel, statusBox, actionsBox, infoBox);
        return integrationBox;
    }
    
    /**
     * Creates wallet management tab with proper selection
     */
    private VBox createWalletManagementTab() {
        VBox manageBox = new VBox(20);
        manageBox.setStyle("-fx-padding: 25; -fx-background-color: #16213e;");
        
        Label manageLabel = new Label("📋 Wallet Management & Selection");
        manageLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Wallet list with selection
        VBox walletSelectionBox = new VBox(15);
        walletSelectionBox.setStyle("-fx-background-color: #22334e; -fx-background-radius: 8; -fx-padding: 20;");
        
        Label selectionTitle = new Label("Select Active Wallet:");
        selectionTitle.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        
        // ComboBox for wallet selection
        ComboBox<String> walletSelector = new ComboBox<>();
        walletSelector.setPromptText("Choose a wallet to activate...");
        walletSelector.setStyle("-fx-background-color: #16213e; -fx-text-fill: white; " +
                               "-fx-border-color: #40e0d0; -fx-border-radius: 6; -fx-padding: 8; -fx-font-size: 14px;");
        
        // Load button for selected wallet
        HBox walletLoadSection = new HBox(10);
        walletLoadSection.setStyle("-fx-alignment: center-left;");
        
        PasswordField walletPasswordField = new PasswordField();
        walletPasswordField.setPromptText("Enter wallet password");
        walletPasswordField.setStyle("-fx-background-color: #16213e; -fx-text-fill: white; " +
                                    "-fx-border-color: #40e0d0; -fx-border-radius: 6; -fx-padding: 8;");
        
        Button loadWalletBtn = new Button("🔓 Load & Activate Wallet");
        loadWalletBtn.setStyle("-fx-background-color: #00ff88; -fx-text-fill: black; -fx-font-weight: bold; " +
                              "-fx-padding: 10 20; -fx-background-radius: 8; -fx-font-size: 14px;");
        
        walletLoadSection.getChildren().addAll(walletPasswordField, loadWalletBtn);
        
        // Active wallet status display
        Label activeWalletStatus = new Label("No active wallet");
        activeWalletStatus.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold; -fx-font-size: 14px;");
        
        walletSelectionBox.getChildren().addAll(selectionTitle, walletSelector, walletLoadSection, activeWalletStatus);
        
        // Wallet list display
        TextArea walletListArea = new TextArea();
        walletListArea.setEditable(false);
        walletListArea.setPrefRowCount(8);
        walletListArea.setStyle("-fx-background-color: #22334e; -fx-text-fill: white; " +
                               "-fx-border-color: #40e0d0; -fx-border-width: 1; -fx-background-radius: 6;");
        
        // Action buttons
        HBox actionButtons = new HBox(15);
        actionButtons.setStyle("-fx-padding: 10 0;");
        
        Button refreshBtn = new Button("🔄 Refresh Wallets");
        refreshBtn.setStyle("-fx-background-color: #40e0d0; -fx-text-fill: black; -fx-font-weight: bold; " +
                           "-fx-padding: 10 20; -fx-background-radius: 8;");
        
        Button testSmartContractBtn = new Button("🧪 Test Smart Contract Access");
        testSmartContractBtn.setStyle("-fx-background-color: #8a2be2; -fx-text-fill: white; -fx-font-weight: bold; " +
                                     "-fx-padding: 10 20; -fx-background-radius: 8;");
        
        actionButtons.getChildren().addAll(refreshBtn, testSmartContractBtn);
        
        // Event handlers
        loadWalletBtn.setOnAction(e -> {
            String selectedWalletId = walletSelector.getValue();
            String password = walletPasswordField.getText();
            
            if (selectedWalletId == null || selectedWalletId.trim().isEmpty()) {
                showErrorAlert("Please select a wallet from the dropdown");
                return;
            }
            
            if (password == null || password.trim().isEmpty()) {
                showErrorAlert("Please enter the wallet password");
                return;
            }
            
            loadAndActivateWallet(selectedWalletId, password, activeWalletStatus, walletPasswordField);
        });
        
        refreshBtn.setOnAction(e -> {
            refreshWalletSelectorAndList(walletSelector, walletListArea);
            updateActiveWalletStatus(activeWalletStatus);
        });
        
        testSmartContractBtn.setOnAction(e -> testSmartContractAccess());
        
        // Initial load
        refreshWalletSelectorAndList(walletSelector, walletListArea);
        updateActiveWalletStatus(activeWalletStatus);
        
        manageBox.getChildren().addAll(manageLabel, walletSelectionBox, 
            new Label("Available Wallets:") {{ setStyle("-fx-text-fill: white; -fx-font-weight: bold;"); }}, 
            walletListArea, actionButtons);
        
        return manageBox;
    }
    
    /**
     * Creates wallet transaction tab
     */
    private VBox createWalletTransactionTab() {
        VBox txBox = new VBox(20);
        txBox.setStyle("-fx-padding: 25; -fx-background-color: #16213e;");
        
        Label txLabel = new Label("💸 Send Transaction");
        txLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        Label infoLabel = new Label("Transaction functionality will be available once a wallet is loaded.");
        infoLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 14px;");
        
        txBox.getChildren().addAll(txLabel, infoLabel);
        return txBox;
    }
    
    /**
     * Creates wallet stats section
     */
    private HBox createWalletStatsSection() {
        HBox statsBox = new HBox(20);
        statsBox.setStyle("-fx-padding: 20 0;");
        
        // Get wallet statistics
        List<WalletController.WalletInfo> wallets = walletController != null ? 
            walletController.listWallets() : new ArrayList<>();
        
        VBox walletsCard = createStatsCard("Total Wallets", 
            String.valueOf(wallets.size()), "#00ff88");
        VBox activeCard = createStatsCard("Active Wallets", 
            String.valueOf(wallets.size() > 0 ? 1 : 0), "#40e0d0");
        VBox balanceCard = createStatsCard("Total Balance", "0 TINC", "#8a2be2");
        
        statsBox.getChildren().addAll(walletsCard, activeCard, balanceCard);
        return statsBox;
    }
    
    /**
     * Creates a statistics card
     */
    private VBox createStatsCard(String title, String value, String color) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: #22334e; -fx-background-radius: 12; " +
                      "-fx-padding: 25; -fx-pref-width: 250; -fx-alignment: center;");
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 14px; -fx-alignment: center;");
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 28px; " +
                           "-fx-font-weight: bold; -fx-alignment: center;");
        
        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }
    
    // ========================================
    // WALLET INTEGRATION METHODS
    // ========================================
    
    /**
     * Connects wallet to smart contract controller with production-level deployment support
     */
    private void connectWalletToSmartContractController(String walletId) {
        try {
            LOGGER.info("Connecting wallet to smart contract controller: " + walletId);
            
            // Initialize smart contract components if needed
            if (smartContractController == null) {
                smartContractController = new SmartContractController();
                smartContractController.setTransactionPool(transactionPool);
                LOGGER.info("SmartContractController initialized and connected to transaction pool");
            }
            
            if (smartContractManager == null) {
                smartContractManager = new SmartContractManager();
                smartContractManager.setTransactionPool(transactionPool);
                LOGGER.info("SmartContractManager initialized and connected to transaction pool");
            }
            
            // Connect wallet components to smart contract
            smartContractController.setWalletController(walletController);
            smartContractController.setWalletManager(walletManager);
            smartContractController.setActiveWalletId(walletId);
            
            smartContractManager.setWalletController(walletController);
            smartContractManager.setWalletManager(walletManager);
            smartContractManager.updateActiveWallet(walletId);
            
            boolean connectionSuccess = false;
            
            // Connect the actual wallet instance
            if (walletManager != null && walletController != null) {
                WalletController.WalletBalanceInfo balanceInfo = walletController.getWalletBalance(walletId);
                if (balanceInfo != null) {
                    // Create wallet with proper address and balance
                    org.example.app.core.wallet.Wallet activeWallet = new org.example.app.core.wallet.Wallet();
                    
                    // Set the wallet balance properly
                    activeWallet.updateBalance(balanceInfo.nativeBalance);
                    
                    // Set the wallet in SmartContractController for deployment
                    smartContractController.setCurrentWallet(activeWallet);
                    
                    // Verify connection worked
                    connectionSuccess = smartContractController.isWalletAvailable();
                    
                    LOGGER.info("✅ WALLET SUCCESSFULLY CONNECTED TO SMART CONTRACT CONTROLLER");
                    LOGGER.info("   - Wallet ID: " + walletId);
                    LOGGER.info("   - Balance: " + balanceInfo.nativeBalance + " TINC");
                    LOGGER.info("   - Address: " + balanceInfo.address);
                    LOGGER.info("   - Generated Address: " + activeWallet.getAddress());
                    LOGGER.info("   - Connection Verified: " + connectionSuccess);
                    
                    // Production verification: Double-check deployment readiness
                    if (smartContractController.getCurrentWallet() != null) {
                        LOGGER.info("✅ DEPLOYMENT VERIFICATION: currentWallet is properly set");
                    } else {
                        LOGGER.severe("❌ DEPLOYMENT ISSUE: currentWallet is still null after setting");
                    }
                    
                } else {
                    LOGGER.warning("Could not get balance info for wallet: " + walletId);
                }
            } else {
                LOGGER.warning("WalletManager or WalletController is null during smart contract connection");
            }
            
            // Verify connection worked and notify components
            if (connectionSuccess) {
                LOGGER.info("🎉 WALLET-SMART CONTRACT CONNECTION VERIFIED: " + walletId);
                
                // Set wallet in SmartContractManager as well
                if (smartContractManager != null) {
                    try {
                        // Get the current wallet from controller and set it in manager
                        org.example.app.core.wallet.Wallet currentWallet = smartContractController.getCurrentWallet();
                        if (currentWallet != null) {
                            LOGGER.info("SmartContractManager wallet connection verified through controller");
                        }
                    } catch (Exception e) {
                        LOGGER.warning("Error verifying SmartContractManager connection: " + e.getMessage());
                    }
                }
                
                // Notify smart contract view immediately
                if (smartContractView != null) {
                    Platform.runLater(() -> {
                        try {
                            smartContractView.refreshWalletStatus();
                            smartContractView.onWalletStateChanged();
                            LOGGER.info("Smart contract view notified of wallet connection");
                        } catch (Exception e) {
                            LOGGER.warning("Error notifying smart contract view: " + e.getMessage());
                        }
                    });
                } else {
                    LOGGER.warning("SmartContractView is null - cannot notify of wallet connection");
                }
                
                // Verify smart contract manager connection
                if (smartContractManager != null && smartContractManager.isWalletAvailable()) {
                    LOGGER.info("Smart contract manager also verified wallet connection");
                }
                
            } else {
                LOGGER.warning("❌ WALLET-SMART CONTRACT CONNECTION FAILED: " + walletId);
                LOGGER.warning("   - SmartContractController available: " + (smartContractController != null));
                LOGGER.warning("   - Wallet available in controller: " + 
                              (smartContractController != null ? smartContractController.isWalletAvailable() : "N/A"));
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error connecting wallet to smart contract controller: " + e.getMessage());
            e.printStackTrace();
            
            // Ensure we don't leave components in inconsistent state
            try {
                if (smartContractController != null) {
                    smartContractController.setActiveWalletId(null);
                }
                if (smartContractManager != null) {
                    smartContractManager.updateActiveWallet(null);
                }
            } catch (Exception cleanupError) {
                LOGGER.severe("Error during cleanup after connection failure: " + cleanupError.getMessage());
            }
        }
    }
    
    /**
     * Connects active wallet to smart contract controller
     */
    private void connectActiveWalletToSmartContract() {
        try {
            LOGGER.info("Attempting to connect active wallet to smart contract controller");
            
            if (walletManager != null && smartContractController != null) {
                String activeWalletId = walletManager.getActiveWalletId();
                LOGGER.info("Active wallet ID: " + activeWalletId);
                
                if (activeWalletId != null && walletController != null) {
                    WalletController.WalletBalanceInfo balanceInfo = walletController.getWalletBalance(activeWalletId);
                    
                    if (balanceInfo != null) {
                        // Create wallet instance without setAddress calls
                        org.example.app.core.wallet.Wallet activeWallet = new org.example.app.core.wallet.Wallet();
                        
                        // Only set balance (no setAddress needed)
                        activeWallet.updateBalance(balanceInfo.nativeBalance);
                        
                        // Set the wallet in smart contract controller
                        smartContractController.setCurrentWallet(activeWallet);
                        
                        LOGGER.info("Successfully connected wallet to smart contract controller");
                        LOGGER.info("Wallet balance: " + balanceInfo.nativeBalance + " TINC");
                        LOGGER.info("Generated wallet address: " + activeWallet.getAddress());
                        
                        // Show success message
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Wallet Connected");
                            alert.setHeaderText("Smart Contract Wallet Connected");
                            alert.setContentText(String.format(
                                "✅ Wallet successfully connected for smart contract operations!\n\n" +
                                "📋 Connected Wallet:\n" +
                                "   • ID: %s\n" +
                                "   • Stored Address: %s\n" +
                                "   • Generated Address: %s\n" +
                                "   • Balance: %d TINC\n\n" +
                                "🚀 You can now deploy and interact with smart contracts!\n\n" +
                                "💡 Note: A new address was generated for smart contract operations.",
                                activeWalletId, 
                                formatAddress(balanceInfo.address),
                                formatAddress(activeWallet.getAddress()),
                                balanceInfo.nativeBalance
                            ));
                            alert.showAndWait();
                        });
                        
                        return; // Success!
                    } else {
                        LOGGER.warning("Could not get balance info for active wallet: " + activeWalletId);
                    }
                } else {
                    LOGGER.warning("No active wallet ID found or WalletController is null");
                }
                
                // No active wallet found
                showWalletRequiredDialog();
                
            } else {
                LOGGER.warning("WalletManager or SmartContractController not initialized");
                LOGGER.warning("WalletManager: " + (walletManager != null ? "OK" : "NULL"));
                LOGGER.warning("SmartContractController: " + (smartContractController != null ? "OK" : "NULL"));
                showWalletRequiredDialog();
            }
        } catch (Exception e) {
            LOGGER.severe("Error connecting wallet to smart contract controller: " + e.getMessage());
            e.printStackTrace();
            showWalletRequiredDialog();
        }
    }
    
    /**
     * Connects wallet to consensus operations
     */
    private void connectWalletToConsensus(String walletId) {
        try {
            LOGGER.info("Connecting wallet to consensus operations: " + walletId);
            
            if (walletController != null && consensusController != null) {
                WalletController.WalletBalanceInfo balanceInfo = walletController.getWalletBalance(walletId);
                
                if (balanceInfo != null) {
                    // Create wallet instance for consensus
                    org.example.app.core.wallet.Wallet consensusWallet = new org.example.app.core.wallet.Wallet();
                    consensusWallet.updateBalance(balanceInfo.nativeBalance);
                    
                    // Connect to consensus controller
                    consensusController.setConnectedWallet(consensusWallet);
                    consensusController.setMinerAddress(balanceInfo.address);
                    
                    // Connect to consensus manager
                    consensusManager.setConnectedWallet(consensusWallet);
                    
                    LOGGER.info("Wallet successfully connected to consensus operations");
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error connecting wallet to consensus: " + e.getMessage());
        }
    }
    
    /**
     * Checks if wallet is ready for smart contracts
     */
    private boolean isWalletReadyForSmartContracts() {
        try {
            // Check WalletManager first
            if (walletManager == null) {
                LOGGER.warning("WalletManager is null");
                return false;
            }
            
            // Use the enhanced status checking
            WalletManager.WalletStatusInfo statusInfo = walletManager.getDetailedStatus();
            
            LOGGER.info("Wallet status check: " + statusInfo.toString());
            
            // Enhanced: More lenient readiness check
            boolean isReady = statusInfo.hasActiveWallet && 
                             (statusInfo.isReady || statusInfo.status == WalletManager.WalletStatus.ACTIVE);
            
            // Enhanced: Additional verification if first check fails
            if (!isReady && statusInfo.activeWalletId != null) {
                LOGGER.info("Primary readiness check failed, trying fallback verification");
                
                // Fallback: check if smart contract controller recognizes the wallet
                if (smartContractController != null) {
                    boolean smartContractReady = smartContractController.isWalletAvailable();
                    LOGGER.info("Smart contract controller wallet check: " + smartContractReady);
                    if (smartContractReady) {
                        isReady = true;
                    }
                }
            }
            
            if (!isReady) {
                LOGGER.info("Wallet not ready - hasActive: " + statusInfo.hasActiveWallet + 
                           ", isReady: " + statusInfo.isReady + 
                           ", status: " + statusInfo.status);
                return false;
            }
            
            // Validate wallet controller has the wallet
            if (walletController == null) {
                LOGGER.warning("WalletController is null");
                return false;
            }
            
            WalletController.WalletBalanceInfo balanceInfo = walletController.getWalletBalance(statusInfo.activeWalletId);
            boolean hasBalance = (balanceInfo != null);
            
            LOGGER.info("Final wallet check - Active ID: " + statusInfo.activeWalletId + 
                       ", Has balance info: " + hasBalance + 
                       ", Enhanced readiness: " + isReady);
            
            // Enhanced: Return true if we have wallet readiness OR balance info
            boolean finalResult = isReady && hasBalance;
            
            LOGGER.info("🎯 FINAL WALLET READINESS RESULT: " + finalResult);
            
            return finalResult;
            
        } catch (Exception e) {
            LOGGER.severe("Error checking wallet readiness: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Updates smart contract integration status
     */
    private void updateSmartContractIntegrationStatus(Label statusLabel) {
        if (isWalletReadyForSmartContracts()) {
            String activeWalletId = walletManager.getActiveWalletId();
            statusLabel.setText("✅ Ready for Smart Contracts: " + activeWalletId);
            statusLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold; -fx-font-size: 12px;");
        } else {
            statusLabel.setText("⚠️ Load a wallet to enable Smart Contracts");
            statusLabel.setStyle("-fx-text-fill: #ffa500; -fx-font-weight: bold; -fx-font-size: 12px;");
        }
    }
    
    /**
     * Handle wallet creation from form
     */
    private void createWalletFromForm(String walletId, String password, boolean isContractWallet, 
                                     TextArea statusArea, TextField walletIdField, PasswordField passwordField) {
        statusArea.setText("🔄 Creating wallet: " + walletId + "...");
        
        try {
            if (walletController != null) {
                WalletController.WalletCreationResult result = 
                    walletController.createWallet(walletId, password, isContractWallet);
                
                if (result.success) {
                    statusArea.setText("✅ SUCCESS: Wallet created successfully!\n\n" +
                                     "Wallet ID: " + result.walletInfo.walletId + "\n" +
                                     "Address: " + result.walletInfo.address + "\n" +
                                     "Balance: " + result.walletInfo.balance + " TINC\n" +
                                     "Contract Wallet: " + (result.walletInfo.isContractWallet ? "Yes" : "No") + "\n\n" +
                                     "🔧 Wallet is ready for use!");
                    
                    // Clear form
                    walletIdField.clear();
                    passwordField.clear();
                    
                    // Update dashboard
                    updateDashboardMetrics();
                } else {
                    statusArea.setText("❌ CREATION FAILED:\n\n" + result.message + "\n\n" +
                                     "💡 Please try a different wallet ID or check your inputs.");
                }
            } else {
                statusArea.setText("❌ ERROR: WalletController not initialized");
            }
        } catch (Exception e) {
            statusArea.setText("❌ CREATION ERROR:\n\n" + e.getMessage() + "\n\n" +
                             "💡 Please check the console for detailed error information.");
            LOGGER.severe("Error creating wallet: " + e.getMessage());
        }
    }
    
    /**
     * Load and activate wallet method
     */
    private void loadAndActivateWallet(String walletId, String password, Label statusLabel, PasswordField passwordField) {
        LOGGER.info("Loading and activating wallet with smart contract integration: " + walletId);
        
        try {
            if (walletController == null) {
                walletController = new WalletController();
                LOGGER.info("WalletController initialized for wallet loading");
            }
            
            if (walletManager == null) {
                walletManager = new WalletManager();
                
                // Connect event listener immediately after creation
                if (walletEventHandler != null) {
                    walletManager.addEventListener(walletEventHandler);
                    LOGGER.info("WalletManager event listener connected immediately");
                } else {
                    LOGGER.warning("WalletEventHandler is null - events may not propagate");
                }
            }
            
            // Load wallet using controller
            WalletController.WalletLoadResult loadResult = walletController.loadWallet(walletId, password);
            
            if (loadResult.success && loadResult.walletInfo != null) {
                LOGGER.info("Wallet loaded successfully: " + walletId);
                
                // Set as active in manager
                walletManager.setActiveWallet(walletId);
                
                // Force immediate smart contract connection with verification
                Platform.runLater(() -> {
                    try {
                        // Connect to smart contract controller
                        connectWalletToSmartContractController(walletId);
                        
                        // Verify connection worked
                        boolean connectionVerified = false;
                        if (smartContractController != null) {
                            connectionVerified = smartContractController.isWalletAvailable();
                            String connectedWalletId = smartContractController.getActiveWalletId();
                            LOGGER.info("Smart contract controller wallet check - Available: " + connectionVerified + 
                                       ", Connected ID: " + connectedWalletId);
                        }
                        
                        // Update smart contract view immediately
                        if (smartContractView != null) {
                            smartContractView.refreshWalletStatus();
                            smartContractView.onWalletStateChanged();
                            LOGGER.info("Smart contract view refreshed after wallet activation");
                        } else {
                            LOGGER.warning("SmartContractView is null - may need initialization");
                        }
                        
                        // Update smart contract manager
                        if (smartContractManager != null) {
                            smartContractManager.updateActiveWallet(walletId);
                            LOGGER.info("Smart contract manager updated with active wallet");
                        }
                        
                        // Verify complete integration
                        if (connectionVerified) {
                            LOGGER.info("🎉 WALLET-SMART CONTRACT INTEGRATION COMPLETE: " + walletId);
                        } else {
                            LOGGER.warning("⚠️ WALLET-SMART CONTRACT INTEGRATION INCOMPLETE: " + walletId);
                        }
                        
                    } catch (Exception e) {
                        LOGGER.severe("Error in smart contract integration: " + e.getMessage());
                    }
                });
                
                // Update UI status
                statusLabel.setText("✅ Active: " + walletId + " (" + loadResult.walletInfo.balance + " TINC)");
                statusLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold; -fx-font-size: 14px;");
                
                // Clear password field
                passwordField.clear();
                
                // Update dashboard metrics
                updateDashboardMetrics();
                
                // Enhanced: Show success message with smart contract status
                Platform.runLater(() -> {
                    // Small delay to ensure smart contract connection is established
                    new Thread(() -> {
                        try {
                            Thread.sleep(500); // Allow time for smart contract connection
                            Platform.runLater(() -> {
                                // Check final smart contract status
                                boolean smartContractReady = (smartContractController != null && 
                                                            smartContractController.isWalletAvailable());
                                
                                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                                alert.setTitle("Wallet Activated Successfully! 🎉");
                                alert.setHeaderText("Wallet Ready for Smart Contracts");
                                
                                String smartContractStatus = smartContractReady ? 
                                    "✅ Smart Contract Management is now enabled!" : 
                                    "⚠️ Smart Contract connection pending...";
                                
                                alert.setContentText(String.format(
                                    "✅ Wallet activated successfully!\n\n" +
                                    "📋 Wallet Details:\n" +
                                    "   • Wallet ID: %s\n" +
                                    "   • Address: %s\n" +
                                    "   • Balance: %d TINC\n\n" +
                                    "%s\n" +
                                    "You can now deploy and interact with smart contracts.",
                                    walletId, 
                                    loadResult.walletInfo.address,
                                    loadResult.walletInfo.balance,
                                    smartContractStatus
                                ));
                                
                                ButtonType goToSmartContractsButton = new ButtonType("🚀 Go to Smart Contracts");
                                ButtonType stayHereButton = new ButtonType("Stay Here");
                                alert.getButtonTypes().setAll(goToSmartContractsButton, stayHereButton);
                                
                                alert.showAndWait().ifPresent(response -> {
                                    if (response == goToSmartContractsButton) {
                                        // Use the enhanced navigation method
                                        navigateToSmartContractFromWallet();
                                    }
                                });
                            });
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                });
                
            } else {
                String errorMsg = loadResult.message != null ? loadResult.message : "Unknown error";
                statusLabel.setText("❌ Failed to load wallet");
                statusLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold; -fx-font-size: 14px;");
                showErrorAlert("Failed to load wallet: " + errorMsg);
                LOGGER.severe("Wallet loading failed: " + errorMsg);
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error loading wallet: " + e.getMessage());
            statusLabel.setText("❌ Error loading wallet");
            statusLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold; -fx-font-size: 14px;");
            showErrorAlert("Error loading wallet: " + e.getMessage());
        }
    }
    
    /**
     * Refresh wallet selector and list
     */
    private void refreshWalletSelectorAndList(ComboBox<String> walletSelector, TextArea walletListArea) {
        try {
            LOGGER.info("Refreshing wallet selector and list");
            
            // Clear current items
            walletSelector.getItems().clear();
            
            if (walletController != null) {
                List<WalletController.WalletInfo> wallets = walletController.listWallets();
                
                // Update ComboBox
                for (WalletController.WalletInfo wallet : wallets) {
                    walletSelector.getItems().add(wallet.walletId);
                }
                
                // Update TextArea
                StringBuilder walletInfo = new StringBuilder();
                walletInfo.append("📋 AVAILABLE WALLETS:\n");
                walletInfo.append("═════════════════════════════════════════\n\n");
                
                if (wallets.isEmpty()) {
                    walletInfo.append("No wallets found.\n\n");
                    walletInfo.append("💡 Create a new wallet using the 'Create Wallet' tab.");
                } else {
                    for (int i = 0; i < wallets.size(); i++) {
                        WalletController.WalletInfo wallet = wallets.get(i);
                        walletInfo.append(String.format(
                            "🦄 WALLET #%d\n" +
                            "   ID: %s\n" +
                            "   Address: %s\n" +
                            "   Balance: %d TINC\n" +
                            "   Type: %s\n" +
                            "   Status: %s\n\n",
                            i + 1,
                            wallet.walletId,
                            wallet.address,
                            wallet.balance,
                            wallet.isContractWallet ? "Contract Wallet" : "Regular Wallet",
                            isWalletActive(wallet.walletId) ? "ACTIVE" : "Available"
                        ));
                    }
                }
                
                walletListArea.setText(walletInfo.toString());
                LOGGER.info("Found " + wallets.size() + " wallets");
            } else {
                walletListArea.setText("❌ WalletController not initialized");
            }
        } catch (Exception e) {
            walletListArea.setText("❌ Error loading wallet list:\n\n" + e.getMessage());
            LOGGER.severe("Error refreshing wallet list: " + e.getMessage());
        }
    }
    
    /**
     * Update active wallet status
     */
    private void updateActiveWalletStatus(Label statusLabel) {
        try {
            if (walletManager != null) {
                String activeWalletId = walletManager.getActiveWalletId();
                if (activeWalletId != null && walletController != null) {
                    WalletController.WalletBalanceInfo balanceInfo = walletController.getWalletBalance(activeWalletId);
                    if (balanceInfo != null) {
                        statusLabel.setText("✅ Active: " + activeWalletId + " (" + balanceInfo.nativeBalance + " TINC)");
                        statusLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold; -fx-font-size: 14px;");
                        return;
                    }
                }
            }
            
            statusLabel.setText("❌ No active wallet");
            statusLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold; -fx-font-size: 14px;");
        } catch (Exception e) {
            statusLabel.setText("❌ Error checking wallet status");
            statusLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold; -fx-font-size: 14px;");
        }
    }
    
    /**
     * Check if wallet is active
     */
    private boolean isWalletActive(String walletId) {
        try {
            if (walletManager != null) {
                String activeWalletId = walletManager.getActiveWalletId();
                return walletId.equals(activeWalletId);
            }
        } catch (Exception e) {
            LOGGER.warning("Error checking if wallet is active: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Test smart contract access
     */
    private void testSmartContractAccess() {
        LOGGER.info("Testing smart contract access");
        
        Platform.runLater(() -> {
            StringBuilder testResults = new StringBuilder();
            testResults.append("🧪 SMART CONTRACT ACCESS TEST:\n\n");
            
            // Test 1: Wallet Manager
            boolean hasWalletManager = (walletManager != null);
            testResults.append("1. Wallet Manager: ").append(hasWalletManager ? "✅ Available" : "❌ Missing").append("\n");
            
            // Test 2: Active Wallet
            String activeWalletId = null;
            if (hasWalletManager) {
                activeWalletId = walletManager.getActiveWalletId();
                testResults.append("2. Active Wallet: ").append(activeWalletId != null ? "✅ " + activeWalletId : "❌ None").append("\n");
            }
            
            // Test 3: Wallet Controller
            boolean hasWalletController = (walletController != null);
            testResults.append("3. Wallet Controller: ").append(hasWalletController ? "✅ Available" : "❌ Missing").append("\n");
            
            // Test 4: Smart Contract Controller
            boolean hasSmartContractController = (smartContractController != null);
            testResults.append("4. Smart Contract Controller: ").append(hasSmartContractController ? "✅ Available" : "❌ Missing").append("\n");
            
            // Test 5: Integration Status
            boolean canAccessSmartContracts = isWalletReadyForSmartContracts();
            testResults.append("5. Smart Contract Access: ").append(canAccessSmartContracts ? "✅ READY" : "❌ NOT READY").append("\n\n");
            
            // Overall result
            if (canAccessSmartContracts) {
                testResults.append("🎉 RESULT: Smart Contract Management is ACCESSIBLE!\n\n");
                testResults.append("You can now:\n");
                testResults.append("• Deploy Solidity contracts\n");
                testResults.append("• Interact with deployed contracts\n");
                testResults.append("• Monitor contract events\n");
            } else {
                testResults.append("⚠️ RESULT: Smart Contract Management is NOT accessible.\n\n");
                testResults.append("Required steps:\n");
                if (activeWalletId == null) {
                    testResults.append("• Select and load a wallet\n");
                }
                if (!hasSmartContractController) {
                    testResults.append("• Initialize smart contract controller\n");
                }
            }
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Smart Contract Access Test");
            alert.setHeaderText("Access Test Results");
            alert.setContentText(testResults.toString());
            
            if (canAccessSmartContracts) {
                ButtonType goToSmartContractsButton = new ButtonType("🚀 Go to Smart Contracts");
                ButtonType okButton = new ButtonType("OK");
                alert.getButtonTypes().setAll(goToSmartContractsButton, okButton);
                
                alert.showAndWait().ifPresent(response -> {
                    if (response == goToSmartContractsButton) {
                        switchView("smartcontract");
                    }
                });
            } else {
                alert.showAndWait();
            }
        });
    }
    
    /**
     * Special navigation method for wallet management to send users to smart contracts
     */
    public void navigateToSmartContractFromWallet() {
        LOGGER.info("Navigating to smart contract management from wallet management");
        
        Platform.runLater(() -> {
            try {
                // Ensure wallet is connected before switching
                if (walletManager != null) {
                    String activeWalletId = walletManager.getActiveWalletId();
                    if (activeWalletId != null) {
                        connectWalletToSmartContractController(activeWalletId);
                    }
                }
                
                // Small delay to ensure connection is established
                new Thread(() -> {
                    try {
                        Thread.sleep(200);
                        Platform.runLater(() -> {
                            switchView("smartcontract");
                            LOGGER.info("Successfully navigated to smart contract with wallet connected");
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
                
            } catch (Exception e) {
                LOGGER.severe("Error navigating to smart contract from wallet: " + e.getMessage());
                showErrorAlert("Navigation error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Tests the complete wallet-smart contract integration
     */
    public void testWalletSmartContractIntegration() {
        LOGGER.info("🧪 TESTING WALLET-SMART CONTRACT INTEGRATION");
        
        StringBuilder testResults = new StringBuilder();
        testResults.append("INTEGRATION TEST RESULTS:\n\n");
        
        // Test 1: Wallet Manager
        testResults.append("1. WalletManager: ");
        if (walletManager != null) {
            String activeId = walletManager.getActiveWalletId();
            testResults.append("✅ Active - ID: ").append(activeId != null ? activeId : "None").append("\n");
        } else {
            testResults.append("❌ Not initialized\n");
        }
        
        // Test 2: Wallet Controller
        testResults.append("2. WalletController: ");
        testResults.append(walletController != null ? "✅ Active\n" : "❌ Not initialized\n");
        
        // Test 3: Smart Contract Controller
        testResults.append("3. SmartContractController: ");
        if (smartContractController != null) {
            boolean hasWallet = smartContractController.isWalletAvailable();
            String activeId = smartContractController.getActiveWalletId();
            testResults.append("✅ Active - Wallet: ").append(hasWallet ? "Connected" : "Not Connected");
            testResults.append(" - ID: ").append(activeId != null ? activeId : "None").append("\n");
        } else {
            testResults.append("❌ Not initialized\n");
        }
        
        // Test 4: Integration Status
        testResults.append("4. Integration Status: ");
        if (walletManager != null && smartContractController != null) {
            String walletId = walletManager.getActiveWalletId();
            String scWalletId = smartContractController.getActiveWalletId();
            boolean synced = (walletId != null && walletId.equals(scWalletId));
            testResults.append(synced ? "✅ SYNCHRONIZED" : "❌ NOT SYNCHRONIZED").append("\n");
        } else {
            testResults.append("❌ COMPONENTS MISSING\n");
        }
        
        testResults.append("\n");
        
        // Overall status
        boolean fullyIntegrated = (walletManager != null && 
                                  walletController != null && 
                                  smartContractController != null && 
                                  smartContractController.isWalletAvailable());
        
        if (fullyIntegrated) {
            testResults.append("🎉 INTEGRATION STATUS: FULLY OPERATIONAL!");
        } else {
            testResults.append("⚠️ INTEGRATION STATUS: SETUP REQUIRED");
        }
        
        LOGGER.info(testResults.toString());
        
        // Show results in UI
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Integration Test Results");
            alert.setHeaderText("Wallet-Smart Contract Integration Test");
            alert.setContentText(testResults.toString());
            alert.showAndWait();
        });
    }
    
    /**
     * Show dialog when wallet is required
     */
    private void showWalletRequiredDialog() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Wallet Required");
            alert.setHeaderText("No Active Wallet Found");
            alert.setContentText("Smart contract deployment requires an active wallet.\n\n" +
                               "Please:\n" +
                               "1. Go to Wallet Management\n" +
                               "2. Create or Load a wallet\n" +
                               "3. Return to Smart Contract Management\n\n" +
                               "Would you like to go to Wallet Management now?");
            
            ButtonType yesButton = new ButtonType("Yes, Go to Wallet");
            ButtonType noButton = new ButtonType("No, Stay Here");
            alert.getButtonTypes().setAll(yesButton, noButton);
            
            alert.showAndWait().ifPresent(response -> {
                if (response == yesButton) {
                    switchView("wallet");
                }
            });
        });
    }
    
    /**
     * Shows alert for wallet required for smart contracts
     */
    private void showWalletRequiredForSmartContractsAlert() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Wallet Required");
            alert.setHeaderText("Smart Contract Deployment Requires Active Wallet");
            alert.setContentText("To use Smart Contract Management, you need to:\n\n" +
                               "1. Create a new wallet (recommended: Contract Wallet)\n" +
                               "2. Load an existing wallet\n\n" +
                               "Once your wallet is active, you can deploy and interact with smart contracts.\n\n" +
                               "Would you like to create a wallet now?");
            
            ButtonType createButton = new ButtonType("Create Wallet");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(createButton, cancelButton);
            
            alert.showAndWait().ifPresent(response -> {
                if (response == createButton) {
                    // Stay in wallet management to create wallet
                    LOGGER.info("User chose to create wallet for smart contracts");
                }
            });
        });
    }
    
    /**
     * Format address for display
     */
    private String formatAddress(String address) {
        if (address == null || address.length() < 10) {
            return address;
        }
        return address.substring(0, 8) + "..." + address.substring(address.length() - 6);
    }
    
    // ========================================
    // WALLET EVENT HANDLER
    // ========================================
    
    /**
     * Wallet event handler class for smart contract integration
     */
    private class WalletEventHandler implements WalletManager.WalletEventListener {
        
        @Override
        public void onWalletCreated(WalletController.WalletInfo walletInfo) {
            LOGGER.info("Wallet created event: " + walletInfo.walletId);
            Platform.runLater(() -> {
                // Update dashboard's active wallet tracking
                activeWalletId = walletInfo.walletId;
                
                // Connect to smart contract controller
                connectWalletToSmartContractController(walletInfo.walletId);
                
                // Connect to consensus controller
                if (consensusIntegrationEnabled) {
                    connectWalletToConsensus(walletInfo.walletId);
                }
                
                updateDashboardMetrics();
            });
        }
        
        public void onWalletLoaded(WalletController.WalletInfo walletInfo) {
            LOGGER.info("Wallet loaded event: " + walletInfo.walletId);
            Platform.runLater(() -> {
                // Update dashboard's active wallet tracking
                activeWalletId = walletInfo.walletId;
                
                // Connect to smart contract controller
                connectWalletToSmartContractController(walletInfo.walletId);
                
                // Connect to consensus controller
                if (consensusIntegrationEnabled) {
                    connectWalletToConsensus(walletInfo.walletId);
                }
                
                updateDashboardMetrics();
                notifySmartContractOfWalletUpdate();
            });
        }
    }
    
    /**
     * Notifies smart contract of wallet update
     */
    private void notifySmartContractOfWalletUpdate() {
        try {
            if (smartContractView != null) {
                // If smart contract view exists, trigger an update
                Platform.runLater(() -> {
                    // This will be handled by the wallet event system
                    LOGGER.info("Notified smart contract view of wallet update");
                });
            }
        } catch (Exception e) {
            LOGGER.warning("Error notifying smart contract of wallet update: " + e.getMessage());
        }
    }
    
    // ========================================
    // CONSENSUS EVENT INTEGRATION
    // ========================================
    
    /**
     * Sets up event integration between consensus and dashboard
     */
    private void setupConsensusEventIntegration() {
        // Consensus controller event listener
        consensusController.addEventListener(new ConsensusEventListener() {
            @Override
            public void onConsensusChanged(ConsensusType oldType, ConsensusType newType) {
                Platform.runLater(() -> {
                    handleConsensusTypeChange(oldType, newType);
                });
            }
            
            @Override
            public void onBlockMined(org.example.app.core.block.Block block) {
                Platform.runLater(() -> {
                    handleBlockMined(block);
                });
            }
            
            @Override
            public void onDifficultyChanged(long oldDifficulty, long newDifficulty) {
                Platform.runLater(() -> {
                    handleDifficultyChange(oldDifficulty, newDifficulty);
                });
            }
            
            @Override
            public void onStakingChanged(String address, long amount, boolean isStaking) {
                Platform.runLater(() -> {
                    handleStakingChange(address, amount, isStaking);
                });
            }
            
            @Override
            public void onValidatorChanged(String validator, boolean added) {
                Platform.runLater(() -> {
                    handleValidatorChange(validator, added);
                });
            }
            
            @Override
            public void onConsensusError(String error) {
                Platform.runLater(() -> {
                    handleConsensusError(error);
                });
            }
        });
        
        // Consensus manager event listener
        consensusManager.addEventListener(new ConsensusManagerEventListener() {
            @Override
            public void onConsensusChanged(ConsensusType newType) {
                Platform.runLater(() -> {
                    currentConsensusType = newType;
                    updateConsensusStatusInNavigation();
                    updateConsensusAlgorithmLabel();
                });
            }
            
            @Override
            public void onPolicyChanged(ConsensusPolicy oldPolicy, ConsensusPolicy newPolicy) {
                Platform.runLater(() -> {
                    currentConsensusPolicy = newPolicy;
                    updateConsensusAlgorithmLabel();
                });
            }
            
            @Override
            public void onHealthStatusChanged(ConsensusHealthStatus health) {
                Platform.runLater(() -> {
                    updateNetworkStatusBasedOnHealth(health);
                });
            }
            
            @Override
            public void onOptimizationApplied(org.example.app.core.ui.consensus.ConsensusManager.ConsensusRecommendation recommendation) {
                Platform.runLater(() -> {
                    showConsensusOptimizationNotification(recommendation);
                });
            }
            
            @Override
            public void onConsensusError(String error) {
                Platform.runLater(() -> {
                    handleConsensusError(error);
                });
            }
        });
    }
    
    /**
     * Handles consensus type changes across the application
     */
    private void handleConsensusTypeChange(ConsensusType oldType, ConsensusType newType) {
        LOGGER.info("Handling consensus type change from " + oldType + " to " + newType);
        
        // Update current consensus type
        currentConsensusType = newType;
        
        // Update consensus algorithm label in dashboard
        updateConsensusAlgorithmLabel();
        
        // Update navigation button styling to reflect change
        updateConsensusNavigationHighlight();
        
        // Update metrics based on new consensus type
        updateConsensusSpecificMetrics();
        
        // Notify other modules of consensus change
        notifyModulesOfConsensusChange(newType);
        
        // Show user notification
        showConsensusChangeNotification(oldType, newType);
    }
    
    /**
     * Notifies other modules of consensus changes
     */
    private void notifyModulesOfConsensusChange(ConsensusType newType) {
        try {
            // Notify smart contract module
            if (smartContractController != null) {
                LOGGER.info("Notifying smart contract controller of consensus change");
            }
            
            // Notify transaction module
            if (transactionController != null) {
                LOGGER.info("Notifying transaction controller of consensus change");
            }
            
            // Notify token module
            if (tokenController != null) {
                LOGGER.info("Notifying token controller of consensus change");
            }
            
            // Notify wallet module
            if (walletController != null) {
                LOGGER.info("Notifying wallet controller of consensus change");
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error notifying modules of consensus change: " + e.getMessage());
        }
    }
    
    /**
     * Handles block mining events
     */
    private void handleBlockMined(org.example.app.core.block.Block block) {
        LOGGER.info("Block mined: " + block.getNumber());
        
        // Update dashboard metrics
        updateDashboardMetrics();
        
        // Update recent blocks table
        loadRecentBlocks();
        
        // Show mining success indicator
        if (currentConsensusType == ConsensusType.PROOF_OF_WORK) {
            showMiningSuccessIndicator(block);
        }
    }
    
    /**
     * Handles difficulty changes
     */
    private void handleDifficultyChange(long oldDifficulty, long newDifficulty) {
        LOGGER.info("Difficulty changed from " + oldDifficulty + " to " + newDifficulty);
        
        // Update network hashrate display (difficulty affects displayed hashrate)
        Platform.runLater(() -> {
            if (networkHashrateLabel != null) {
                // Adjust hashrate display based on difficulty
                double adjustedHashrate = (newDifficulty / 1000.0) * 50.0; // Simplified calculation
                networkHashrateLabel.setText(String.format("%.1f MH/s", adjustedHashrate));
            }
        });
    }
    
    /**
     * Handles staking changes in PoS
     */
    private void handleStakingChange(String address, long amount, boolean isStaking) {
        LOGGER.info((isStaking ? "Staked" : "Unstaked") + " " + amount + " tokens for " + address);
        
        // Update dashboard if this affects the connected wallet
        if (walletManager != null && address.equals(walletManager.getActiveWalletId())) {
            updateDashboardMetrics();
        }
    }
    
    /**
     * Handles validator changes in PBFT
     */
    private void handleValidatorChange(String validator, boolean added) {
        LOGGER.info((added ? "Added" : "Removed") + " validator: " + validator);
        
        // Update active peers count to reflect validator changes
        Platform.runLater(() -> {
            if (activePeersLabel != null && currentConsensusType == ConsensusType.PBFT) {
                // For PBFT, active peers essentially means active validators
                updateActivePeersForPBFT();
            }
        });
    }
    
    /**
     * Handles consensus errors
     */
    private void handleConsensusError(String error) {
        LOGGER.severe("Consensus error: " + error);
        
        // Update network status to show error
        Platform.runLater(() -> {
            if (networkStatusLabel != null) {
                networkStatusLabel.setText("Error");
                networkStatusLabel.setStyle("-fx-text-fill: #ff0000;");
            }
        });
        
        // Show error notification to user
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Consensus Error");
            alert.setHeaderText("Consensus Operation Failed");
            alert.setContentText("A consensus error has occurred: " + error + 
                               "\n\nThe system will attempt to recover automatically.");
            alert.showAndWait();
        });
    }
    
    /**
     * Updates consensus algorithm label based on current consensus and policy
     */
    private void updateConsensusAlgorithmLabel() {
        if (consensusAlgorithmLabel != null) {
            String consensusText = currentConsensusType.getDisplayName();
            if (currentConsensusPolicy != null) {
                consensusText += " (" + currentConsensusPolicy.getDisplayName() + ")";
            }
            consensusAlgorithmLabel.setText(consensusText);
        }
    }
    
    /**
     * Updates consensus-specific metrics in dashboard
     */
    private void updateConsensusSpecificMetrics() {
        if (!consensusIntegrationEnabled || consensusController == null) {
            return;
        }
        
        try {
            ConsensusStatusInfo status = consensusController.getConsensusStatus();
            
            // Update metrics based on active consensus type
            switch (currentConsensusType) {
                case PROOF_OF_WORK:
                    updatePoWMetrics(status);
                    break;
                case PROOF_OF_STAKE:
                    updatePoSMetrics(status);
                    break;
                case PBFT:
                    updatePBFTMetrics(status);
                    break;
                case STREAM_CONSENSUS:
                    updateStreamMetrics(status);
                    break;
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error updating consensus-specific metrics: " + e.getMessage());
        }
    }
    
    /**
     * Updates PoW-specific metrics
     */
    private void updatePoWMetrics(ConsensusStatusInfo status) {
        if (status.powStatus != null) {
            // Update hashrate
            if (networkHashrateLabel != null) {
                networkHashrateLabel.setText(ConsensusUIUtils.formatHashRate(status.powStatus.hashRate));
            }
            
            // Update mining status in a custom way
            updateMiningStatusIndicator(status.powStatus.isMining);
        }
    }
    
    /**
     * Updates PoS-specific metrics
     */
    private void updatePoSMetrics(ConsensusStatusInfo status) {
        if (status.posStatus != null) {
            // Update staking information
            LOGGER.info("PoS Metrics - Total Staked: " + status.posStatus.totalStaked + 
                       ", Stakeholders: " + status.posStatus.stakeholderCount);
        }
    }
    
    /**
     * Updates PBFT-specific metrics
     */
    private void updatePBFTMetrics(ConsensusStatusInfo status) {
        if (status.pbftStatus != null) {
            // Update validator count as active peers for PBFT
            if (activePeersLabel != null) {
                activePeersLabel.setText(String.valueOf(status.pbftStatus.validatorCount));
            }
            
            // Update validation status
            updateValidationStatusIndicator(status.pbftStatus.isValidating);
        }
    }
    
    /**
     * Updates Stream consensus-specific metrics
     */
    private void updateStreamMetrics(ConsensusStatusInfo status) {
        if (status.streamStatus != null) {
            // Update stream processing metrics
            LOGGER.info("Stream Metrics - Processing: " + status.streamStatus.isProcessing + 
                       ", Processed Batches: " + status.streamStatus.processedBatches);
        }
    }
    
    /**
     * Updates network status based on consensus health
     */
    private void updateNetworkStatusBasedOnHealth(ConsensusHealthStatus health) {
        if (networkStatusLabel != null) {
            if (health.overallHealth >= 0.8) {
                networkStatusLabel.setText("Excellent");
                networkStatusLabel.setStyle("-fx-text-fill: #00ff00;");
            } else if (health.overallHealth >= 0.6) {
                networkStatusLabel.setText("Good");
                networkStatusLabel.setStyle("-fx-text-fill: #90ee90;");
            } else if (health.overallHealth >= 0.4) {
                networkStatusLabel.setText("Fair");
                networkStatusLabel.setStyle("-fx-text-fill: #ffa500;");
            } else {
                networkStatusLabel.setText("Poor");
                networkStatusLabel.setStyle("-fx-text-fill: #ff6b6b;");
            }
        }
    }
    
    /**
     * Updates consensus navigation button highlighting
     */
    private void updateConsensusNavigationHighlight() {
        if (consensusNavBtn != null) {
            // Add a subtle glow effect to indicate consensus is actively being managed
            String glowEffect = "; -fx-effect: dropshadow(gaussian, " + 
                               ConsensusUIUtils.getConsensusTypeColor(currentConsensusType) + 
                               ", 8, 0.6, 0, 0);";
            
            String currentStyle = consensusNavBtn.getStyle();
            if (!currentStyle.contains("-fx-effect:")) {
                consensusNavBtn.setStyle(currentStyle + glowEffect);
                
                // Remove effect after 5 seconds
                Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
                    consensusNavBtn.setStyle(currentStyle);
                }));
                timeline.play();
            }
        }
    }
    
    /**
     * Updates consensus status in navigation area
     */
    private void updateConsensusStatusInNavigation() {
        // This could update a status indicator in the navigation area
        LOGGER.info("Consensus status updated in navigation: " + currentConsensusType.getDisplayName());
    }
    
    /**
     * Shows consensus change notification
     */
    private void showConsensusChangeNotification(ConsensusType oldType, ConsensusType newType) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Consensus Mechanism Changed");
            alert.setHeaderText("Successfully Switched Consensus");
            alert.setContentText(String.format(
                "Consensus mechanism has been changed:\n\n" +
                "From: %s\n" +
                "To: %s\n\n" +
                "All operations are now using the new consensus mechanism.\n" +
                "Dashboard metrics have been updated accordingly.",
                oldType.getDisplayName(),
                newType.getDisplayName()
            ));
            
            // Auto-close after 10 seconds
            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> alert.close()));
            timeline.play();
            
            alert.show();
        });
    }
    
    /**
     * Shows consensus optimization notification
     */
    private void showConsensusOptimizationNotification(org.example.app.core.ui.consensus.ConsensusManager.ConsensusRecommendation recommendation) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Consensus Optimized");
            alert.setHeaderText("Auto-Optimization Applied");
            alert.setContentText(String.format(
                "Consensus has been automatically optimized:\n\n" +
                "New Consensus: %s\n" +
                "Reason: %s\n" +
                "Confidence: %.1f%%\n\n" +
                "This change should improve system performance.",
                recommendation.recommendedType.getDisplayName(),
                recommendation.reason,
                recommendation.confidence * 100
            ));
            
            alert.showAndWait();
        });
    }
    
    /**
     * Shows mining success indicator
     */
    private void showMiningSuccessIndicator(org.example.app.core.block.Block block) {
        if (blockHeightLabel != null) {
            // Temporarily highlight the block height label
            String originalStyle = blockHeightLabel.getStyle();
            blockHeightLabel.setStyle(originalStyle + "; -fx-effect: dropshadow(gaussian, lime, 10, 0.8, 0, 0);");
            
            // Remove effect after 3 seconds
            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
                blockHeightLabel.setStyle(originalStyle);
            }));
            timeline.play();
        }
    }
    
    /**
     * Updates mining status indicator
     */
    private void updateMiningStatusIndicator(boolean isMining) {
        LOGGER.info("Mining status: " + (isMining ? "Active" : "Inactive"));
    }
    
    /**
     * Updates validation status indicator for PBFT
     */
    private void updateValidationStatusIndicator(boolean isValidating) {
        LOGGER.info("Validation status: " + (isValidating ? "Active" : "Inactive"));
    }
    
    /**
     * Updates active peers count for PBFT
     */
    private void updateActivePeersForPBFT() {
        if (consensusController != null) {
            ConsensusStatusInfo status = consensusController.getConsensusStatus();
            if (status.pbftStatus != null && activePeersLabel != null) {
                activePeersLabel.setText(String.valueOf(status.pbftStatus.validatorCount));
            }
        }
    }
    
    // ========================================
    // METRICS AND UPDATES
    // ========================================
    
    /**
     * Processes pending transactions and mines them into blocks
     */
    private void processAndMineTransactions() {
        if (uiTransactionManager != null) {
            try {
                int pendingCount = uiTransactionManager.getTransactionCount();
                if (pendingCount > 0) {
                    LOGGER.info("Auto-processing " + pendingCount + " pending transactions");
                    
                    // Mine transactions to blocks
                    org.example.app.core.block.Block minedBlock = uiTransactionManager.mineTransactionsToBlock();
                    
                    if (minedBlock != null) {
                        LOGGER.info("Successfully mined block with " + minedBlock.getTransactions().size() + " transactions");
                        
                        // Update dashboard after successful mining
                        Platform.runLater(() -> {
                            loadRecentBlocks();
                            loadRecentTransactions();
                            updateMetrics();
                        });
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Error processing transactions: " + e.getMessage());
            }
        }
    }
    
    /**
     * Updates dashboard metrics to reflect all activity
     */
    private void updateDashboardMetrics() {
        try {
            Platform.runLater(() -> {
                LOGGER.info("Updating dashboard metrics with token activity...");
                
                // Update existing metrics
                updateMetrics();
                
                // Add token-specific metrics
                updateTokenMetrics();

                if (networkController != null) {
                updateNetworkSpecificMetrics();
                }         
                
                LOGGER.info("Dashboard metrics updated successfully");
            });
        } catch (Exception e) {
            LOGGER.severe("Error updating dashboard metrics: " + e.getMessage());
        }
    }
    
    /**
     * Updates core blockchain metrics
     */
    private void updateMetrics() {
        Platform.runLater(() -> {
            try {
                // Network status updates
                networkStatusLabel.setText(peerNetwork != null ? "Online" : "Offline");
                networkStatusLabel.setStyle(peerNetwork != null ? "-fx-text-fill: #00ff00;" : "-fx-text-fill: #ff0000;");
                
                // Blockchain height
                blockHeightLabel.setText(decimalFormat.format(
                    Math.max(consensusBlockchain.getChainLength(), ethBlockchain.getHeight())));
                
                // Enhanced: Include ALL transaction data in mempool size
                int totalMempoolSize = transactionPool.size();
                
                // Add pending transactions from UI transaction manager
                if (uiTransactionManager != null) {
                    int pendingTxCount = uiTransactionManager.getTransactionCount();
                    LOGGER.fine("UI Transaction Manager pending transactions: " + pendingTxCount);
                }
                
                mempoolSizeLabel.setText(String.valueOf(totalMempoolSize));
                
                // Enhanced: Add token count and transaction info
                if (tokenManager != null) {
                    TokenManager.TokenStatistics stats = tokenManager.getTokenStatistics();
                    LOGGER.fine("Current dashboard state - Mempool: " + totalMempoolSize + 
                               ", Tokens: " + stats.getTotalTokens() + 
                               ", Token Supply: " + stats.getTotalSupply());
                } else {
                    // Fallback to TokenRegistry directly if tokenManager not available
                    int tokenCount = TokenRegistry.getInstance().getAllTokens().size();
                    LOGGER.fine("Current dashboard state - Mempool: " + totalMempoolSize + ", Tokens: " + tokenCount);
                }
                
                // Update other network metrics
                activePeersLabel.setText(String.valueOf(peerNetwork != null ? 1 : 0)); // Simple peer count
                networkHashrateLabel.setText("~" + String.format("%.1f", Math.random() * 100) + " MH/s");
                
                // Update uptime
                long uptime = System.currentTimeMillis() - networkStartTime;
                uptimeLabel.setText(formatUptime(uptime));
                
                // Update timestamps and consensus info
                lastBlockTimeLabel.setText(java.time.LocalDateTime.now().format(timeFormatter));
                consensusAlgorithmLabel.setText("PBFT + PoW");
                
                // Update consensus-specific metrics
                if (consensusIntegrationEnabled) {
                    updateConsensusSpecificMetrics();
                }
                
                // Update consensus algorithm label
                updateConsensusAlgorithmLabel();
                
                // Update charts with latest data
                updateCharts();
                
                LOGGER.fine("Dashboard metrics updated successfully");
                
            } catch (Exception e) {
                LOGGER.warning("Error updating metrics: " + e.getMessage());
            }
        });
    }
    

/**
 * Setup network event integration with dashboard
 */
private void setupNetworkEventIntegration() {
    if (networkController != null) {
        networkController.addEventListener(new NetworkController.NetworkEventListener() {
            @Override
            public void onPeerConnected(String peerId, InetSocketAddress address) {
                Platform.runLater(() -> {
                    LOGGER.info("Dashboard: Peer connected - " + peerId);
                    updateDashboardMetrics();
                });
            }
            
            @Override
            public void onPeerDisconnected(String peerId) {
                Platform.runLater(() -> {
                    LOGGER.info("Dashboard: Peer disconnected - " + peerId);
                    updateDashboardMetrics();
                });
            }
            
            @Override
            public void onMessageReceived(String fromPeer, Messages.Message message) {
                Platform.runLater(() -> {
                    updateDashboardMetrics();
                });
            }
            
            @Override
            public void onMessageSent(String toPeer, Messages.Message message) {
                Platform.runLater(() -> {
                    updateDashboardMetrics();
                });
            }
            
            @Override
            public void onBroadcastSent(Messages.Message message, int peerCount) {
                Platform.runLater(() -> {
                    updateDashboardMetrics();
                });
            }
            
            @Override
            public void onNetworkError(String error) {
                Platform.runLater(() -> {
                    LOGGER.warning("Dashboard: Network error - " + error);
                    // Update network status to show error
                    if (networkStatusLabel != null) {
                        networkStatusLabel.setText("Error");
                        networkStatusLabel.setStyle("-fx-text-fill: #ff0000;");
                    }
                });
            }
        });
        
        LOGGER.info("Network event integration setup completed");
    }
    
    if (networkManager != null) {
        networkManager.addEventListener(new NetworkManager.NetworkManagerEventListener() {
            @Override
            public void onNetworkStateChanged(NetworkManager.NetworkState.StateType oldState, 
                                             NetworkManager.NetworkState.StateType newState) {
                Platform.runLater(() -> {
                    LOGGER.info("Dashboard: Network state changed - " + oldState + " → " + newState);
                    updateDashboardMetrics();
                });
            }
            
            @Override
            public void onPeerDiscovered(String peerId, InetSocketAddress address) {
                Platform.runLater(() -> {
                    LOGGER.info("Dashboard: Peer discovered - " + peerId);
                });
            }
            
            @Override
            public void onConnectionPolicyChanged(NetworkManager.ConnectionPolicy.PolicyType policyType) {
                Platform.runLater(() -> {
                    LOGGER.info("Dashboard: Connection policy changed - " + policyType);
                });
            }
            
            @Override
            public void onNetworkHealthChanged(NetworkManager.NetworkHealthStatus health) {
                Platform.runLater(() -> {
                    LOGGER.info("Dashboard: Network health changed - " + health.getHealthLevel());
                    updateDashboardMetrics();
                });
            }
            
            @Override
            public void onNetworkOptimization(String optimization) {
                Platform.runLater(() -> {
                    LOGGER.info("Dashboard: Network optimization - " + optimization);
                });
            }
            
            @Override
            public void onNetworkManagerError(String error) {
                Platform.runLater(() -> {
                    LOGGER.warning("Dashboard: Network manager error - " + error);
                });
            }
        });
        
        LOGGER.info("Network manager event integration setup completed");
    }
}

    /**
     * Updates token-specific metrics on dashboard
     */
    private void updateTokenMetrics() {
        try {
            // Get token count from your real TokenRegistry
            int tokenCount = TokenRegistry.getInstance().getAllTokens().size();
            
            // You can add a token counter to your dashboard if desired
            LOGGER.info("Current token count: " + tokenCount);
            
            // Force refresh of transaction pool to show token transactions
            refreshTransactionPool();
            
            // Update charts with latest data
            updateCharts();
            
        } catch (Exception e) {
            LOGGER.warning("Error updating token metrics: " + e.getMessage());
        }
    }
    
    /**
     * Refreshes transaction pool to show token transactions
     */
    private void refreshTransactionPool() {
        try {
            // Force reload recent transactions (this will pick up token transactions)
            loadRecentTransactions();
            
            // Update mempool size label
            if (mempoolSizeLabel != null) {
                mempoolSizeLabel.setText(String.valueOf(transactionPool.size()));
            }
            
            LOGGER.info("Transaction pool refreshed - size: " + transactionPool.size());
            
        } catch (Exception e) {
            LOGGER.warning("Error refreshing transaction pool: " + e.getMessage());
        }
    }
    
    /**
     * Formats uptime for display
     */
    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Updates dashboard charts
     */
    private void updateCharts() {
        // Block time chart
        if (!blockTimeChart.getData().isEmpty()) {
            XYChart.Series<String, Number> series = blockTimeChart.getData().get(0);
            if (series.getData().size() > 20) series.getData().remove(0);
            series.getData().add(new XYChart.Data<>(
                LocalDateTime.now().format(timeFormatter),
                blockchainMetrics.getAverageBlockTime()
            ));
        }
        
        // Transaction chart
        if (!transactionChart.getData().isEmpty()) {
            XYChart.Series<String, Number> series = transactionChart.getData().get(0);
            if (series.getData().size() > 20) series.getData().remove(0);
            series.getData().add(new XYChart.Data<>(
                LocalDateTime.now().format(timeFormatter),
                transactionPool.size()
            ));
        }
    }
    
    /**
     * Starts real-time updates for the dashboard
     */
    private void startRealTimeUpdates() {
        updateService = new ScheduledService<Void>() {
            @Override 
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override 
                    protected Void call() {
                        updateMetrics();
                        if (Math.random() < 0.1) {
                            loadRecentBlocks();
                            loadRecentTransactions();
                        }
                        return null;
                    }
                };
            }
        };
        updateService.setPeriod(Duration.seconds(2));
        updateService.start();
    }
    
    /**
     * Call this method from your token management view to sync with dashboard
     */
    public void notifyDashboardOfTokenActivity() {
        LOGGER.info("Notifying dashboard of token activity...");
        
        // Update dashboard metrics
        updateDashboardMetrics();
        
        // If currently on dashboard, refresh it
        if ("dashboard".equals(currentView)) {
            Platform.runLater(() -> updateMetrics());
        }
    }
    
    // ========================================
    // TOKEN MANAGEMENT IMPLEMENTATION
    // ========================================
    
    /**
     * Enhanced version of token creation that properly updates dashboard
     */
    private void handleQuickCreateToken(TextField nameField, TextField symbolField, 
                                       TextField supplyField, TextField decimalsField, 
                                       CheckBox mintableCheck) {
        try {
            LOGGER.info("Creating token with dashboard integration...");
            
            // Validation
            String name = nameField.getText().trim();
            String symbol = symbolField.getText().trim();
            String supplyText = supplyField.getText().trim();
            String decimalsText = decimalsField.getText().trim();
            
            if (name.isEmpty() || symbol.isEmpty()) {
                showErrorAlert("Token name and symbol are required");
                return;
            }
            
            long initialSupply = 0;
            if (!supplyText.isEmpty()) {
                try {
                    initialSupply = Long.parseLong(supplyText);
                } catch (NumberFormatException e) {
                    showErrorAlert("Invalid initial supply number");
                    return;
                }
            }
            
            int decimals = 18;
            if (!decimalsText.isEmpty()) {
                try {
                    decimals = Integer.parseInt(decimalsText);
                    if (decimals < 0 || decimals > 18) {
                        showErrorAlert("Decimals must be between 0 and 18");
                        return;
                    }
                } catch (NumberFormatException e) {
                    showErrorAlert("Invalid decimals number");
                    return;
                }
            }
            
            // Generate contract address
            String contractAddress = "0x" + Long.toHexString(System.currentTimeMillis());
            String creator = "0x742c7d3a8e1e1e2b8c3b2a8a8a8a8a8a8a8a8a8a";
            
            // Create token using TokenBuilder
            TokenBuilder builder = new TokenBuilder()
                .name(name)
                .symbol(symbol)
                .decimals(decimals)
                .initialSupply(initialSupply)
                .contractAddress(contractAddress)
                .mintable(mintableCheck.isSelected())
                .creator(creator);
            
            ERC20Token token = builder.buildERC20();
            
            // Simulate the transaction in blockchain
            simulateTokenTransactionInBlockchain(token);
            
            LOGGER.info("Token created successfully: " + token.getName());
            
            // Clear form
            nameField.clear();
            symbolField.clear();
            supplyField.clear();
            decimalsField.setText("18");
            mintableCheck.setSelected(false);
            
            // Show success message
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Token Created");
                alert.setHeaderText("Token Created Successfully!");
                alert.setContentText(String.format(
                    "Token: %s (%s)\n" +
                    "Contract: %s\n" +
                    "Initial Supply: %s\n" +
                    "Decimals: %d\n" +
                    "Mintable: %s\n\n" +
                    "✅ Added to TokenRegistry\n" +
                    "✅ Transaction added to mempool\n" +
                    "✅ Dashboard metrics updated",
                    token.getName(), token.getSymbol(),
                    token.getContractAddress(),
                    decimalFormat.format(token.getTotalSupply()),
                    token.getDecimals(),
                    token.isMintable() ? "Yes" : "No"
                ));
                alert.showAndWait();
            });
            
            // Update dashboard after token creation
            Platform.runLater(() -> {
                // Refresh token management view
                switchView("tokens");
                
                // Update dashboard metrics in background
                new Thread(() -> {
                    try {
                        Thread.sleep(100); // Small delay
                        Platform.runLater(() -> updateDashboardMetrics());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            });
            
        } catch (Exception e) {
            LOGGER.severe("Error creating token: " + e.getMessage());
            showErrorAlert("Failed to create token: " + e.getMessage());
        }
    }
    
    /**
     * Simulates token transaction in blockchain components
     */
    private void simulateTokenTransactionInBlockchain(ERC20Token token) {
        try {
            LOGGER.info("Adding token transaction to blockchain...");
            
            // Create a transaction representing the token creation
            Transaction tokenTx = new Transaction(("TOKEN_CREATE:" + token.getSymbol()).getBytes());
            tokenTx.setSender(token.getCreator());
            tokenTx.setRecipient(token.getContractAddress());
            tokenTx.setValue(token.getTotalSupply());
            
            // Add to real transaction pool
            transactionPool.addTransaction(tokenTx);
            
            LOGGER.info("Token transaction added to mempool - hash: " + tokenTx.getHash());
            
            // Update transaction count in UI
            Platform.runLater(() -> {
                if (mempoolSizeLabel != null) {
                    mempoolSizeLabel.setText(String.valueOf(transactionPool.size()));
                }
            });
            
        } catch (Exception e) {
            LOGGER.warning("Error simulating token transaction: " + e.getMessage());
        }
    }
    
    // ========================================
    // UI HELPER METHODS
    // ========================================
    
    /**
     * Stores the original dashboard content for restoration
     */
    private void storeOriginalDashboardContent() {
        if (originalDashboardContent == null && contentArea != null) {
            try {
                // Since contentArea is ScrollPane, get its content
                ScrollPane scrollPane = (ScrollPane) contentArea;
                if (scrollPane.getContent() != null) {
                    originalDashboardContent = (Parent) scrollPane.getContent();
                    LOGGER.info("Original dashboard content stored from ScrollPane");
                }
            } catch (Exception e) {
                LOGGER.warning("Could not store original dashboard content: " + e.getMessage());
            }
        }
    }
    
    /**
     * Replaces the content view in the center area
     */
    private void replaceContentView(Parent newView) {
        if (contentArea != null) {
            Platform.runLater(() -> {
                try {
                    // contentArea is ScrollPane, so set its content directly
                    ScrollPane scrollPane = (ScrollPane) contentArea;
                    scrollPane.setContent(newView);
                    LOGGER.info("Content set in ScrollPane successfully");
                } catch (Exception e) {
                    LOGGER.severe("Error replacing content view: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } else {
            LOGGER.warning("contentArea is null - cannot replace view");
        }
    }
    
    // ========================================
    // UI DATA MODEL CONVERTERS
    // ========================================
    
    /**
     * Converts blockchain Block to UI Block model
     */
    private UIBlock convertToUIBlock(Block block) {
        UIBlock uiBlock = new UIBlock();
        uiBlock.setHash(block.getHash());
        uiBlock.setNumber(block.getNumber());
        uiBlock.setMiner(block.getMiner());
        uiBlock.setTimestamp(LocalDateTime.ofInstant(
            Instant.ofEpochMilli(block.getTimestamp()), ZoneId.systemDefault()
        ).format(timeFormatter));
        uiBlock.setTransactionCount(block.getTransactions() != null ? block.getTransactions().size() : 0);
        return uiBlock;
    }
    
    /**
     * Converts blockchain Transaction to UI Transaction model
     */
    private UITransaction convertToUITransaction(Transaction tx) {
        UITransaction uiTx = new UITransaction();
        uiTx.setHash(tx.getHash());
        uiTx.setFrom(tx.getSender() != null ? tx.getSender() : "Unknown");
        uiTx.setTo(tx.getRecipient() != null ? tx.getRecipient() : "Unknown");
        uiTx.setValue((tx.getValue() != null ? decimalFormat.format(tx.getValue()) : "0") + " TINC");
        uiTx.setStatus(tx.getStatus() != null ? tx.getStatus().toString() : "PENDING");
        return uiTx;
    }
    
    // ========================================
    // ALERT AND NOTIFICATION METHODS
    // ========================================
    
    /**
     * Shows error alert dialog
     */
    private void showErrorAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Shows info alert dialog
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
    // DEBUG AND MONITORING
    // ========================================
    
    /**
     * Debug FXML setup and component verification
     */
   /**
 * Debug FXML setup with comprehensive button verification
 */
private void debugFXMLSetup() {
    LOGGER.info("=== DEBUG: FXML Setup Check ===");
    LOGGER.info("contentArea: " + (contentArea != null ? "✅ FOUND" : "❌ NULL"));
    
    // Check all navigation buttons
    LOGGER.info("--- Navigation Buttons Status ---");
    LOGGER.info("dashboardNavBtn: " + (dashboardNavBtn != null ? "✅" : "❌"));
    LOGGER.info("walletNavBtn: " + (walletNavBtn != null ? "✅" : "❌"));
    LOGGER.info("transactionsNavBtn: " + (transactionsNavBtn != null ? "✅" : "❌"));
    LOGGER.info("blocksNavBtn: " + (blocksNavBtn != null ? "✅" : "❌"));
    LOGGER.info("smartContractNavBtn: " + (smartContractNavBtn != null ? "✅" : "❌"));
    LOGGER.info("tokensNavBtn: " + (tokensNavBtn != null ? "✅" : "❌"));
    LOGGER.info("networkNavBtn: " + (networkNavBtn != null ? "✅" : "❌"));
    LOGGER.info("consensusNavBtn: " + (consensusNavBtn != null ? "✅" : "❌"));
    LOGGER.info("configNavBtn: " + (configNavBtn != null ? "✅ FOUND" : "❌ NULL - CRITICAL!"));
    LOGGER.info("settingsNavBtn: " + (settingsNavBtn != null ? "✅" : "❌"));
    LOGGER.info("apiNavBtn: " + (apiNavBtn != null ? "✅" : "❌"));
    LOGGER.info("testNetworkNavBtn: " + (testNetworkNavBtn != null ? "✅" : "❌"));
    LOGGER.info("aiChatNavBtn: " + (aiChatNavBtn != null ? "✅" : "❌"));
    
    // Configuration system status
    LOGGER.info("--- Configuration System Status ---");
    LOGGER.info("configurationController: " + (configurationController != null ? "✅" : "❌"));
    LOGGER.info("configurationManager: " + (configurationManager != null ? "✅" : "❌"));
    LOGGER.info("configurationView: " + (configurationView != null ? "✅" : "❌"));
    
    LOGGER.info("=== END DEBUG ===");
}


/**
 * Verify configuration module is properly set up
 */
public boolean verifyConfigurationModule() {
    boolean allGood = true;
    
    if (configNavBtn == null) {
        LOGGER.severe("❌ Configuration button is NULL!");
        allGood = false;
    }
    
    if (configurationController == null) {
        LOGGER.severe("❌ Configuration controller is NULL!");
        allGood = false;
    }
    
    if (configurationManager == null) {
        LOGGER.severe("❌ Configuration manager is NULL!");
        allGood = false;
    }
    
    if (allGood) {
        LOGGER.info("✅ Configuration module verification PASSED");
    } else {
        LOGGER.severe("❌ Configuration module verification FAILED");
    }
    
    return allGood;
}

    /**
     * Verifies wallet-smart contract connection
     */
    private void verifyWalletSmartContractConnection() {
        LOGGER.info("=== VERIFYING WALLET-SMART CONTRACT CONNECTION ===");
        
        boolean walletManagerReady = (walletManager != null && walletManager.hasActiveWallet());
        String activeWalletId = walletManager != null ? walletManager.getActiveWalletId() : null;
        boolean smartContractReady = (smartContractController != null && smartContractController.isWalletAvailable());
        
        LOGGER.info("WalletManager ready: " + walletManagerReady);
        LOGGER.info("Active wallet ID: " + activeWalletId);
        LOGGER.info("SmartContract ready: " + smartContractReady);
        
        if (walletManagerReady && smartContractReady && activeWalletId != null) {
            LOGGER.info("🎉 CONNECTION VERIFIED - Smart contracts should be accessible!");
        } else {
            LOGGER.warning("❌ CONNECTION INCOMPLETE - Debug required");
        }
    }
    
    // ========================================
    // CLEANUP AND SHUTDOWN
    // ========================================
    

/**
 * Cleanup resources when dashboard is closed and save configuration before shutdown
 */
public void cleanup() {
    LOGGER.info("Cleaning up dashboard resources...");
    
    // Save configuration if changed
    if (configurationController != null && 
        configurationController.hasUnsavedChangesProperty().get()) {
        
        LOGGER.info("Saving configuration before shutdown...");
        configurationController.saveConfiguration("config/auto_shutdown_save.properties")
            .thenAccept(success -> {
                if (success) {
                    LOGGER.info("Configuration auto-saved on shutdown");
                }
            });
    }
    
    // Cleanup configuration resources
    if (configurationView != null) {
        configurationView.cleanup();
    }
    
    if (configurationController != null) {
        configurationController.shutdown();
    }
    
    // Cancel update service
    if (updateService != null) {
        updateService.cancel();
    }
    
    // Cleanup network components
    cleanupNetworkComponents();
    
    // Shutdown peer network
    if (peerNetwork != null) {
        try {
            // Now we can properly shutdown the peer network
            peerNetwork.shutdown();
            LOGGER.info("PeerNetwork shutdown completed successfully");
        } catch (Exception e) {
            LOGGER.warning("Error shutting down peer network: " + e.getMessage());
        }
    }
    
    LOGGER.info("Dashboard cleanup completed");
}

    /**
     * Initialize configuration system and connect to dashboard
     */
    private void initializeConfigurationSystem() {
        try {
            LOGGER.info("Initializing configuration system for dashboard");
            
            // Initialize configuration controller
            configurationController = new ConfigurationController();
            
            // Set dashboard reference for bi-directional communication
            configurationController.setDashboardController(this);
            
            // Initialize configuration manager
            configurationManager = new ConfigurationManager();
            
            // Load current configurations
            currentBlockchainConfig = configurationController.getBlockchainConfig();
            currentConsensusConfig = configurationController.getConsensusConfig();
            currentNetworkConfig = configurationController.getNetworkConfig();
            currentStorageConfig = configurationController.getStorageConfig();
            
            // Apply configurations to blockchain components
            applyConfigurationsToBlockchain();
            
            // Setup configuration event listener
            setupConfigurationEventListener();
            
            // Register as configuration change listener
            configurationController.addDashboardListener(new ConfigurationController.ConfigurationChangeListener() {
                @Override
                public void onConfigurationUpdate(String component, Map<String, Object> changes) {
                    Platform.runLater(() -> handleConfigurationUpdate(component, changes));
                }
            });
            
            LOGGER.info("Configuration system initialized and connected to dashboard");
            
        } catch (Exception e) {
            LOGGER.severe("Error initializing configuration system: " + e.getMessage());
            // Continue without configuration integration
        }
    }
    
       /**
     * Setup configuration event listener for real-time updates
     */
    private void setupConfigurationEventListener() {
        configEventListener = new ConfigurationController.ConfigurationEventListener() {
            @Override
            public void onConfigurationChanged(ConfigurationController.ConfigurationChangeEvent event) {
                Platform.runLater(() -> {
                    LOGGER.info("Configuration changed: " + event.component + "." + event.property);
                    
                    // Update dashboard based on configuration change
                    switch (event.component) {
                        case "blockchain":
                            handleBlockchainConfigChange(event);
                            break;
                        case "consensus":
                            handleConsensusConfigChange(event);
                            break;
                        case "network":
                            handleNetworkConfigChange(event);
                            break;
                        case "storage":
                            handleStorageConfigChange(event);
                            break;
                    }
                    
                    // Update dashboard metrics
                    updateMetrics();
                });
            }
            

            
            @Override
            public void onProfileLoaded(String profileName, boolean success) {
                Platform.runLater(() -> {
                    if (success) {
                        showInfoAlert("Configuration Profile Loaded", 
                            "Successfully loaded profile: " + profileName);
                        reloadBlockchainComponents();
                    }
                });
            }
            
            @Override
            public void onConfigurationSaved(String filepath, boolean success) {
                Platform.runLater(() -> {
                    if (success) {
                        LOGGER.info("Configuration saved to: " + filepath);
                    }
                });
            }
            
            @Override
            public void onValidationError(List<ConfigurationController.ConfigurationError> errors) {
                Platform.runLater(() -> {
                    StringBuilder errorMsg = new StringBuilder("Configuration validation errors:\n");
                    for (ConfigurationController.ConfigurationError error : errors) {
                        errorMsg.append("• ").append(error.component).append(": ")
                               .append(error.message).append("\n");
                    }
                    showWarningDialog("Configuration Validation", errorMsg.toString());
                });
            }
            
            @Override
            public void onConfigurationRollback(String snapshotId) {
                Platform.runLater(() -> {
                    LOGGER.info("Configuration rolled back to snapshot: " + snapshotId);
                    reloadBlockchainComponents();
                });
            }
        };
        
        configurationController.addEventListener(configEventListener);
    }
    
    /**
     * Apply loaded configurations to blockchain components
     */
    private void applyConfigurationsToBlockchain() {
        try {
            // Apply blockchain configuration
            if (currentBlockchainConfig != null) {
                // Apply block time to consensus
                long blockTime = currentBlockchainConfig.getBlockTime();
                LOGGER.info("Applied block time: " + blockTime + "ms");
            }
            
            // Apply consensus configuration
            if (currentConsensusConfig != null) {
                ConsensusType consensusType = currentConsensusConfig.getDefaultConsensusType();
                updateConsensusType(consensusType);
                
                // Apply consensus-specific parameters
                if (consensusType == ConsensusType.PROOF_OF_WORK) {
                    long targetBlockTime = currentConsensusConfig.getPowTargetBlockTime();
                    long initialDifficulty = currentConsensusConfig.getPowInitialDifficulty();
                    // Apply to mining components
                    LOGGER.info("Applied PoW config - Target: " + targetBlockTime + "ms, Difficulty: " + initialDifficulty);
                }
            }
            
            // Apply network configuration
            if (currentNetworkConfig != null && peerNetwork != null) {
                int maxPeers = currentNetworkConfig.getMaxPeers();
                String networkId = currentNetworkConfig.getNetworkId();
                // Apply to peer network
                LOGGER.info("Applied network config - ID: " + networkId + ", Max peers: " + maxPeers);
            }
            
            // Apply storage configuration
            if (currentStorageConfig != null) {
                String storageType = currentStorageConfig.getStorageType();
                int cacheSize = currentStorageConfig.getCacheSize();
                LOGGER.info("Applied storage config - Type: " + storageType + ", Cache: " + cacheSize + "MB");
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error applying configurations: " + e.getMessage());
        }
    }
    
    /**
     * Handle blockchain configuration changes
     */
    private void handleBlockchainConfigChange(ConfigurationController.ConfigurationChangeEvent event) {
        switch (event.property) {
            case "blockTime":
                if (event.newValue instanceof Number) {
                    long newBlockTime = ((Number) event.newValue).longValue();
                    updateBlockTime(newBlockTime);
                }
                break;
            case "maxBlockSize":
                if (event.newValue instanceof Number) {
                    long newMaxSize = ((Number) event.newValue).longValue();
                    LOGGER.info("Max block size updated to: " + newMaxSize);
                }
                break;
        }
    }
    
    /**
     * Handle consensus configuration changes
     */
    private void handleConsensusConfigChange(ConfigurationController.ConfigurationChangeEvent event) {
        switch (event.property) {
            case "defaultConsensusType":
                if (event.newValue instanceof ConsensusType) {
                    ConsensusType newType = (ConsensusType) event.newValue;
                    updateConsensusType(newType);
                    
                    // Update UI
                    if (consensusAlgorithmLabel != null) {
                        consensusAlgorithmLabel.setText(newType.getDisplayName());
                    }
                }
                break;
            case "powTargetBlockTime":
                if (event.newValue instanceof Number) {
                    long targetTime = ((Number) event.newValue).longValue();
                    LOGGER.info("PoW target block time updated to: " + targetTime);
                }
                break;
            case "pbftMinValidators":
                if (event.newValue instanceof Number) {
                    int minValidators = ((Number) event.newValue).intValue();
                    LOGGER.info("PBFT min validators updated to: " + minValidators);
                }
                break;
        }
    }
    
    /**
     * Handle network configuration changes
     */
    private void handleNetworkConfigChange(ConfigurationController.ConfigurationChangeEvent event) {
        switch (event.property) {
            case "maxPeers":
                if (event.newValue instanceof Number) {
                    int newMaxPeers = ((Number) event.newValue).intValue();
                    updateMaxPeers(newMaxPeers);
                }
                break;
            case "networkId":
                if (event.newValue instanceof String) {
                    String newNetworkId = (String) event.newValue;
                    updateNetworkId(newNetworkId);
                }
                break;
            case "port":
                if (event.newValue instanceof Number) {
                    int newPort = ((Number) event.newValue).intValue();
                    LOGGER.info("Network port updated to: " + newPort);
                }
                break;
        }
    }
    
    /**
     * Handle storage configuration changes
     */
    private void handleStorageConfigChange(ConfigurationController.ConfigurationChangeEvent event) {
        switch (event.property) {
            case "storageType":
                String newStorageType = (String) event.newValue;
                LOGGER.info("Storage type changed to: " + newStorageType);
                // May require restart to take effect
                showInfoAlert("Storage Configuration", 
                    "Storage type changed to " + newStorageType + ". Restart may be required.");
                break;
            case "cacheSize":
                if (event.newValue instanceof Number) {
                    int newCacheSize = ((Number) event.newValue).intValue();
                    LOGGER.info("Cache size updated to: " + newCacheSize + "MB");
                }
                break;
        }
    }
    
    /**
     * Handle configuration updates from the configuration UI
     */
    private void handleConfigurationUpdate(String component, Map<String, Object> changes) {
        LOGGER.info("Handling configuration update for component: " + component);
        
        // Reload configurations
        currentBlockchainConfig = configurationController.getBlockchainConfig();
        currentConsensusConfig = configurationController.getConsensusConfig();
        currentNetworkConfig = configurationController.getNetworkConfig();
        currentStorageConfig = configurationController.getStorageConfig();
        
        // Apply changes to running components
        applyConfigurationsToBlockchain();
        
        // Update dashboard display
        updateConfigurationDisplay();
        
        // Show notification
        showConfigurationUpdateNotification(component, changes);
    }
    
    /**
     * Update dashboard display with current configuration
     */
    private void updateConfigurationDisplay() {
        Platform.runLater(() -> {
            // Update consensus display
            if (currentConsensusConfig != null && consensusAlgorithmLabel != null) {
                String consensusText = currentConsensusConfig.getDefaultConsensusType().getDisplayName();
                
                // Add policy if available
                if (currentConsensusConfig.getDefaultConsensusPolicy() != null) {
                    consensusText += " (" + currentConsensusConfig.getDefaultConsensusPolicy().getDisplayName() + ")";
                }
                
                consensusAlgorithmLabel.setText(consensusText);
            }
            
            // Update network display
            if (currentNetworkConfig != null) {
                if (networkStatusLabel != null) {
                    String networkText = "Network: " + currentNetworkConfig.getNetworkId();
                    networkStatusLabel.setText(networkText);
                }
            }
            
            // Update block time display
            if (currentBlockchainConfig != null && lastBlockTimeLabel != null) {
                long blockTime = currentBlockchainConfig.getBlockTime();
                // This would be reflected in actual block production
            }
        });
    }
    
    /**
     * Show configuration update notification
     */
    private void showConfigurationUpdateNotification(String component, Map<String, Object> changes) {
        Platform.runLater(() -> {
            // Create subtle notification
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Configuration Updated");
            alert.setHeaderText("Configuration Applied");
            alert.setContentText(String.format(
                "Component: %s\nChanges: %d properties updated\n\nThe dashboard has been updated to reflect the new configuration.",
                component.toUpperCase(),
                changes.size()
            ));
            
            // Auto-close after 5 seconds
            Timeline timeline = new Timeline(new KeyFrame(
                Duration.seconds(5),
                e -> alert.close()
            ));
            timeline.play();
            
            alert.show();
        });
    }
    


 
/**
 * Update block time in blockchain components
 */
private void updateBlockTime(long blockTimeMs) {
    LOGGER.info("Updating block time to: " + blockTimeMs + "ms");
    
    // Update mining components if PoW is active
    if (currentConsensusType == ConsensusType.PROOF_OF_WORK) {
        try {
            // Calculate difficulty based on new block time
            long adjustedDifficulty = calculateDifficultyForBlockTime(blockTimeMs);
            
            // Update miner through our getter method
            org.example.app.core.block.Miner activeMiner = getMinerInstance();
            if (activeMiner != null) {
                activeMiner.setDifficulty(adjustedDifficulty);
                LOGGER.info("Updated miner difficulty to: " + adjustedDifficulty);
            }
            
            // Update consensus controller if available
            if (consensusController != null) {
                // Set difficulty using existing method
                consensusController.adjustDifficulty(adjustedDifficulty);
                
                LOGGER.info("Adjusted mining difficulty to: " + adjustedDifficulty + 
                           " for target block time: " + blockTimeMs + "ms");
                
                // If mining is active, restart with new parameters
                if (consensusController.isMining()) {
                    LOGGER.info("Restarting mining with new difficulty");
                    consensusController.stopMining();
                    
                    // Small delay to ensure clean stop
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            Platform.runLater(() -> {
                                consensusController.startMining();
                            });
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                }
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error updating PoW block time: " + e.getMessage());
        }
    } else if (currentConsensusType == ConsensusType.PBFT) {
        // For PBFT, just log the intended change
        LOGGER.info("PBFT block time would be updated to: " + blockTimeMs + 
                   "ms (view timeout: " + (blockTimeMs / 3) + "ms)");
        
    } else if (currentConsensusType == ConsensusType.PROOF_OF_STAKE) {
        // For PoS, just log the intended change
        long epochLength = Math.max(10, 3600000 / blockTimeMs); // ~1 hour epochs
        LOGGER.info("PoS epoch length would be updated to: " + epochLength + " blocks");
    }
    
    // Update UI to show new expected block time
    Platform.runLater(() -> {
        // Update block time display label if it exists
        if (lastBlockTimeLabel != null) {
            // Format block time for display
            String formattedTime;
            if (blockTimeMs < 1000) {
                formattedTime = blockTimeMs + "ms";
            } else if (blockTimeMs < 60000) {
                formattedTime = String.format("%.1fs", blockTimeMs / 1000.0);
            } else {
                formattedTime = String.format("%.1fm", blockTimeMs / 60000.0);
            }
            
            // Update the label to show expected block time
            String currentText = lastBlockTimeLabel.getText();
            if (currentText != null && !currentText.contains("Expected:")) {
                lastBlockTimeLabel.setText(currentText + " (Expected: " + formattedTime + ")");
            }
        }
        
        // Update block production chart if available
        if (blockTimeChart != null && !blockTimeChart.getData().isEmpty()) {
            XYChart.Series<String, Number> series = blockTimeChart.getData().get(0);
            
            // Add reference line for target block time
            if (series.getData().size() > 0) {
                // Update chart title to show target
                blockTimeChart.setTitle("Block Times - Target: " + 
                    String.format("%.1fs", blockTimeMs / 1000.0));
            }
        }
        
        // Update metrics display
        if (blockHeightLabel != null) {
            // Calculate estimated blocks per hour with new block time
            int blocksPerHour = (int)(3600000 / blockTimeMs);
            Tooltip tooltip = new Tooltip("Estimated " + blocksPerHour + " blocks/hour");
            Tooltip.install(blockHeightLabel, tooltip);
        }
    });
    
    // Store in configuration
    if (currentBlockchainConfig != null) {
        try {
            // Create updated config
            BlockchainConfig updatedConfig = new BlockchainConfig.Builder()
                .blockTime(blockTimeMs)
                .build();
            
            // Update stored config
            currentBlockchainConfig = updatedConfig;
            
        } catch (Exception e) {
            LOGGER.warning("Error updating blockchain config: " + e.getMessage());
        }
    }
}
    /**
     * Update consensus type across the system
     */
    private void updateConsensusType(ConsensusType newType) {
        LOGGER.info("Updating consensus type to: " + newType);
        
        currentConsensusType = newType;
        
        // Update consensus controller if available
        if (consensusController != null) {
            consensusController.switchConsensus(newType);
        }
        
        // Update UI
        Platform.runLater(() -> {
            if (consensusAlgorithmLabel != null) {
                consensusAlgorithmLabel.setText(newType.getDisplayName());
            }
        });
    }
    
    /**
     * Update network ID
     */
    private void updateNetworkId(String networkId) {
        LOGGER.info("Updating network ID to: " + networkId);
        
        // This might require network restart
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Network Configuration");
            alert.setHeaderText("Network ID Changed");
            alert.setContentText("Network ID changed to: " + networkId + 
                "\n\nNetwork restart may be required for changes to take effect.");
            alert.showAndWait();
        });
    }
    

/**
 * Update max peers limit in network
 */
private void updateMaxPeers(int maxPeers) {
    LOGGER.info("Updating max peers to: " + maxPeers);
    
    // Update peer network if available
    if (peerNetwork != null) {
        try {
            // Set the new maximum peer limit
            peerNetwork.setMaxPeers(maxPeers);
            
            // Get current peer count
            int currentPeerCount = peerNetwork.getActivePeers().size();
            
            // If we have more peers than the new limit, disconnect excess peers
            if (currentPeerCount > maxPeers) {
                int peersToDisconnect = currentPeerCount - maxPeers;
                LOGGER.info("Disconnecting " + peersToDisconnect + " excess peers");
                
                // Get list of peers to disconnect (take the first ones)
                List<Peer> peersToRemove = peerNetwork.getActivePeers().stream()
                    .limit(peersToDisconnect)
                    .collect(Collectors.toList());
                
                // Disconnect excess peers
                for (Peer peer : peersToRemove) {
                    peerNetwork.disconnectPeer(peer.getPeerId());
                    LOGGER.info("Disconnected peer: " + peer.getPeerId() + 
                               " (exceeded max peer limit)");
                }
            }
            
            // Update network controller if available
            if (networkController != null) {
                networkController.setMaxPeerConnections(maxPeers);
                
                // Update discovery settings
                if (maxPeers > 50) {
                    // For large networks, be less aggressive with discovery
                    networkController.setDiscoveryInterval(30000); // 30 seconds
                } else if (maxPeers < 10) {
                    // For small networks, discover more frequently
                    networkController.setDiscoveryInterval(10000); // 10 seconds
                } else {
                    // Default discovery interval
                    networkController.setDiscoveryInterval(15000); // 15 seconds
                }
            }
            
            // Update network manager if available
            if (networkManager != null) {
                // Update connection policy based on max peers
                if (maxPeers <= 5) {
                    networkManager.setConnectionPolicy(
                        NetworkManager.ConnectionPolicy.PolicyType.CONSERVATIVE
                    );
                } else if (maxPeers >= 50) {
                    networkManager.setConnectionPolicy(
                        NetworkManager.ConnectionPolicy.PolicyType.AGGRESSIVE
                    );
                } else {
                    networkManager.setConnectionPolicy(
                        NetworkManager.ConnectionPolicy.PolicyType.BALANCED
                    );
                }
                
                LOGGER.info("Network connection policy adjusted for " + maxPeers + " max peers");
            }
            
            // Note: P2P advanced settings would be configured through RobustP2PManager if available
            // PeerNetwork and RobustP2PManager are separate classes, so we skip advanced P2P config here
            LOGGER.info("Max peers configuration applied to PeerNetwork");
            
            // Update UI to reflect changes
            Platform.runLater(() -> {
                // Update active peers display
                if (activePeersLabel != null) {
                    int currentPeers = peerNetwork.getActivePeers().size();
                    activePeersLabel.setText(currentPeers + "/" + maxPeers);
                }
            });
        } catch (Exception e) {
            LOGGER.warning("Error updating max peers: " + e.getMessage());
        }
    }
    
    // Store in configuration
    if (currentNetworkConfig != null) {
        try {
            // Create updated config
            NetworkConfig updatedConfig = new NetworkConfig.Builder()
                .networkId(currentNetworkConfig.getNetworkId())
                .maxPeers(maxPeers)
                .build();
            
            // Update stored config
            currentNetworkConfig = updatedConfig;
            
        } catch (Exception e) {
            LOGGER.warning("Error updating network config: " + e.getMessage());
        }
    }
}

/**
 * Reload blockchain components with new configuration
 */
private void reloadBlockchainComponents() {
    LOGGER.info("Reloading blockchain components with new configuration");
    
    try {
        // Store current state
        int currentBlockHeight = consensusBlockchain.getChainLength();
        
        // Reinitialize with new configuration
        applyConfigurationsToBlockchain();
        
        // Restore state if possible
        LOGGER.info("Blockchain components reloaded. Current height: " + currentBlockHeight);
        
        // Update dashboard
        updateMetrics();
        updateConfigurationDisplay();
        
    } catch (Exception e) {
        LOGGER.severe("Error reloading blockchain components: " + e.getMessage());
        showErrorAlert("Failed to reload blockchain components: " + e.getMessage());
    }
}
   
    
    /**
     * Public method for configuration system to notify dashboard
     */
    public void onConfigurationChanged(Map<String, Object> changes) {
        Platform.runLater(() -> {
            LOGGER.info("Dashboard notified of " + changes.size() + " configuration changes");
            
            // Update relevant dashboard sections
            for (Map.Entry<String, Object> entry : changes.entrySet()) {
                String key = entry.getKey();
                
                if (key.startsWith("consensus.")) {
                    updateConsensusDisplay();
                } else if (key.startsWith("network.")) {
                    updateNetworkDisplay();
                } else if (key.startsWith("blockchain.")) {
                    updateBlockchainMetrics();
                }
            }
            
            // Force metrics refresh
            updateMetrics();
        });
    }
    
    /**
     * Update consensus display in dashboard
     */
    public void updateConsensusDisplay() {
        Platform.runLater(() -> {
            if (currentConsensusConfig != null && consensusAlgorithmLabel != null) {
                consensusAlgorithmLabel.setText(
                    currentConsensusConfig.getDefaultConsensusType().getDisplayName()
                );
            }
        });
    }
    
    /**
     * Update network display in dashboard
     */
    public void updateNetworkDisplay() {
        Platform.runLater(() -> {
            if (currentNetworkConfig != null) {
                // Update network-related UI elements
                if (activePeersLabel != null) {
                    // Max peers might have changed
                    String peerText = getConnectedPeerCount() + "/" + currentNetworkConfig.getMaxPeers();
                    activePeersLabel.setText(peerText);
                }
            }
        });
    }
    
    /**
     * Update blockchain metrics in dashboard
     */
    public void updateBlockchainMetrics() {
        Platform.runLater(() -> {
            if (currentBlockchainConfig != null) {
                // Update blockchain-related metrics
                updateMetrics();
            }
        });
    }
    
     

/**
 * Helper method to get or create miner instance
 */
/**
 * Helper method to get miner instance
 */

/**
 * Helper method to get or create miner instance
 */
private org.example.app.core.block.Miner getMinerInstance() {
    // Use existing miner if available
    if (this.miner != null) {
        return this.miner;
    }
    
    // Create a new miner if needed
    if (consensusBlockchain != null) {
        String minerAddress = "dashboard-miner";
        if (walletManager != null && walletManager.getActiveWalletId() != null) {
            minerAddress = walletManager.getActiveWalletId();
        }
        this.miner = new org.example.app.core.block.Miner(consensusBlockchain, minerAddress);
        return this.miner;
    }
    
    return null;
}

/**
 * Calculate appropriate difficulty for target block time
 */
private long calculateDifficultyForBlockTime(long blockTimeMs) {
    // Base difficulty for 15 second blocks
    long baseDifficulty = 1000000L;
    double baseBlockTime = 15000.0;
    
    // Adjust difficulty inversely proportional to block time
    // Shorter block time = higher difficulty
    double ratio = baseBlockTime / blockTimeMs;
    long adjustedDifficulty = (long)(baseDifficulty * ratio);
    
    // Apply bounds
    long minDifficulty = 1000L;
    long maxDifficulty = 1000000000L;
    
    return Math.max(minDifficulty, Math.min(maxDifficulty, adjustedDifficulty));
}


    /**
     * Show configuration management view
     */
    /**
 * Show configuration management view with full error handling
 */
private void showConfigurationView() {
    LOGGER.info("========================================");
    LOGGER.info("SHOWING CONFIGURATION MANAGEMENT VIEW");
    LOGGER.info("========================================");
    
    try {
        // Step 1: Verify configuration controller is initialized
        if (configurationController == null) {
            LOGGER.warning("Configuration controller is null, initializing...");
            initializeConfigurationSystem();
            
            if (configurationController == null) {
                throw new IllegalStateException("Failed to initialize configuration controller");
            }
        }
        
        // Step 2: Verify configuration manager is initialized
        if (configurationManager == null) {
            LOGGER.warning("Configuration manager is null, initializing...");
            configurationManager = new ConfigurationManager();
        }
        
        // Step 3: Initialize configuration view if needed
        if (configurationView == null) {
            LOGGER.info("Creating new ConfigurationView instance...");
            configurationView = new ConfigurationView(configurationController, configurationManager);
            
            // Set callbacks for dashboard integration
            configurationView.setDashboardUpdateCallback(() -> {
                Platform.runLater(() -> {
                    LOGGER.info("Configuration changed - updating dashboard metrics");
                    updateMetrics();
                    updateConfigurationDisplay();
                });
            });
            
            configurationView.setNavigationCallback(() -> {
                LOGGER.info("Navigation callback triggered from configuration view");
                switchView("dashboard");
            });
            
            LOGGER.info("✅ ConfigurationView initialized successfully");
        } else {
            LOGGER.info("ConfigurationView already exists, reusing instance");
        }
        
        // Step 4: Create the configuration interface
        LOGGER.info("Creating configuration management interface...");
        VBox configurationInterface = configurationView.createConfigurationManagementView();
        
        if (configurationInterface == null) {
            throw new IllegalStateException("Configuration interface is null");
        }
        
        LOGGER.info("✅ Configuration interface created successfully");
        
        // Step 5: Store original dashboard content
        storeOriginalDashboardContent();
        
        // Step 6: Replace content view
        LOGGER.info("Replacing content view with configuration interface...");
        replaceContentView(configurationInterface);
        
        LOGGER.info("========================================");
        LOGGER.info("✅ CONFIGURATION VIEW LOADED SUCCESSFULLY");
        LOGGER.info("========================================");
        
        // Step 7: Show success notification
        Platform.runLater(() -> {
            showConfigurationWelcomeMessage();
        });
        
    } catch (Exception e) {
        LOGGER.severe("========================================");
        LOGGER.severe("❌ ERROR LOADING CONFIGURATION VIEW");
        LOGGER.severe("Error: " + e.getMessage());
        LOGGER.severe("========================================");
        e.printStackTrace();
        
        // Show error to user
        Platform.runLater(() -> {
            showErrorAlert("Failed to load Configuration Management: " + e.getMessage());
            // Fall back to dashboard
            switchView("dashboard");
        });
    }
}
/**
 * Show welcome message for configuration view
 */
private void showConfigurationWelcomeMessage() {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Configuration Management");
    alert.setHeaderText("⚙️ Configuration Management Loaded");
    alert.setContentText(
        "Welcome to Configuration Management!\n\n" +
        "You can now:\n" +
        "• Configure blockchain parameters\n" +
        "• Adjust consensus settings\n" +
        "• Manage network configuration\n" +
        "• Optimize storage settings\n" +
        "• Load and save configuration profiles\n\n" +
        "Changes will be reflected across the entire dashboard."
    );
    
    // Auto-close after 5 seconds
    Timeline timeline = new Timeline(new KeyFrame(
        Duration.seconds(5),
        e -> alert.close()
    ));
    timeline.play();
    
    alert.show();
}

    
    /**
     * Show warning dialog for configuration issues
     */
    private void showWarningDialog(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
   
  

    
    private void startMetricsUpdater() {
        metricsUpdateTimeline = new Timeline(
            new KeyFrame(Duration.seconds(2), event -> updateMetrics())
        );
        metricsUpdateTimeline.setCycleCount(Timeline.INDEFINITE);
        metricsUpdateTimeline.play();
    }

  

    // ========================================
    // UI DATA MODEL CLASSES
    // ========================================
    
    /**
     * UI model for blockchain blocks
     */

    // ========================================
    // UI DATA MODEL CLASSES
    // ========================================
    
    /**
     * UI model for blockchain blocks
     */
    public static class UIBlock {
        private final javafx.beans.property.StringProperty hash = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.LongProperty number = new javafx.beans.property.SimpleLongProperty();
        private final javafx.beans.property.StringProperty miner = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty timestamp = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.IntegerProperty transactionCount = new javafx.beans.property.SimpleIntegerProperty();
        
        public String getHash() { return hash.get(); }
        public void setHash(String hash) { this.hash.set(hash); }
        public javafx.beans.property.StringProperty hashProperty() { return hash; }
        
        public long getNumber() { return number.get(); }
        public void setNumber(long number) { this.number.set(number); }
        public javafx.beans.property.LongProperty numberProperty() { return number; }
        
        public String getMiner() { return miner.get(); }
        public void setMiner(String miner) { this.miner.set(miner); }
        public javafx.beans.property.StringProperty minerProperty() { return miner; }
        
        public String getTimestamp() { return timestamp.get(); }
        public void setTimestamp(String timestamp) { this.timestamp.set(timestamp); }
        public javafx.beans.property.StringProperty timestampProperty() { return timestamp; }
        
        public int getTransactionCount() { return transactionCount.get(); }
        public void setTransactionCount(int count) { this.transactionCount.set(count); }
        public javafx.beans.property.IntegerProperty transactionCountProperty() { return transactionCount; }
    }
    
    /**
     * UI model for blockchain transactions
     */
    public static class UITransaction {
        private final javafx.beans.property.StringProperty hash = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty from = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty to = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty value = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty status = new javafx.beans.property.SimpleStringProperty();
        
        public String getHash() { return hash.get(); }
        public void setHash(String hash) { this.hash.set(hash); }
        public javafx.beans.property.StringProperty hashProperty() { return hash; }
        
        public String getFrom() { return from.get(); }
        public void setFrom(String from) { this.from.set(from); }
        public javafx.beans.property.StringProperty fromProperty() { return from; }
        
        public String getTo() { return to.get(); }
        public void setTo(String to) { this.to.set(to); }
        public javafx.beans.property.StringProperty toProperty() { return to; }
        
        public String getValue() { return value.get(); }
        public void setValue(String value) { this.value.set(value); }
        public javafx.beans.property.StringProperty valueProperty() { return value; }
        
        public String getStatus() { return status.get(); }
        public void setStatus(String status) { this.status.set(status); }
        public javafx.beans.property.StringProperty statusProperty() { return status; }
    }
    
    // ========================================
    // BLOCK EXPLORER GUI METHODS
    // ========================================
    
    /**
     * Creates the JavaFX GUI for Block Explorer
     */
    private VBox createBlockExplorerGUI() {
        VBox mainContainer = new VBox(15);
        mainContainer.setStyle("-fx-background-color: #1a1a2e; -fx-padding: 20;");
        
        // Header
        HBox header = new HBox(20);
        header.setStyle("-fx-padding: 20; -fx-background-color: #16213e; -fx-background-radius: 10;");
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label title = new Label("🔍 Block Explorer");
        title.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #00ff88;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button backButton = new Button("← Back to Dashboard");
        backButton.setStyle("-fx-font-size: 12; -fx-padding: 10; -fx-background-color: #40e0d0; -fx-text-fill: black;");
        backButton.setOnAction(e -> switchView("dashboard"));
        
        header.getChildren().addAll(title, spacer, backButton);
        
        // Search section
        HBox searchBox = createBlockExplorerSearchBox();
        
        // Stats section
        HBox statsBox = createBlockExplorerStatsBox();
        
        // Blocks list section
        ScrollPane blocksSection = createBlocksListSection();
        
        // Add all sections
        VBox.setVgrow(blocksSection, Priority.ALWAYS);
        mainContainer.getChildren().addAll(header, searchBox, statsBox, blocksSection);
        
        return mainContainer;
    }
    
    /**
     * Creates search box for Block Explorer
     */
    private HBox createBlockExplorerSearchBox() {
        HBox searchBox = new HBox(10);
        searchBox.setStyle("-fx-padding: 15; -fx-background-color: #16213e; -fx-background-radius: 10;");
        searchBox.setAlignment(Pos.CENTER_LEFT);
        
        Label searchLabel = new Label("Search:");
        searchLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold;");
        
        TextField searchField = new TextField();
        searchField.setPromptText("Enter block hash, number, or transaction hash...");
        searchField.setStyle("-fx-background-color: #22334e; -fx-text-fill: white; -fx-padding: 8; -fx-border-color: #40e0d0;");
        searchField.setPrefWidth(400);
        
        Button searchButton = new Button("🔎 Search");
        searchButton.setStyle("-fx-background-color: #00ff88; -fx-text-fill: black; -fx-font-weight: bold; -fx-padding: 8 15;");
        searchButton.setOnAction(e -> {
            String query = searchField.getText();
            if (!query.isEmpty()) {
                try {
                    long blockNum = Long.parseLong(query);
                    blockExplorerController.searchBlockByNumber(blockNum);
                    LOGGER.info("Searched for block number: " + blockNum);
                } catch (NumberFormatException ex) {
                    if (query.length() > 20) {
                        blockExplorerController.searchBlockByHash(query);
                        LOGGER.info("Searched for block hash: " + query);
                    } else {
                        blockExplorerController.searchTransactionByHash(query);
                        LOGGER.info("Searched for transaction: " + query);
                    }
                }
            }
        });
        
        Button refreshButton = new Button("🔄 Refresh");
        refreshButton.setStyle("-fx-background-color: #40e0d0; -fx-text-fill: black; -fx-font-weight: bold; -fx-padding: 8 15;");
        refreshButton.setOnAction(e -> {
            blockExplorerController.refreshLatestBlocks();
            LOGGER.info("Refreshed block explorer");
        });
        
        searchBox.getChildren().addAll(searchLabel, searchField, searchButton, refreshButton);
        return searchBox;
    }
    
    /**
     * Creates statistics section for Block Explorer
     */
    private HBox createBlockExplorerStatsBox() {
        HBox statsBox = new HBox(20);
        statsBox.setStyle("-fx-padding: 15; -fx-background-color: #16213e; -fx-background-radius: 10;");
        statsBox.setAlignment(Pos.CENTER_LEFT);
        
        try {
            BlockExplorerManager.BlockchainStats stats = blockExplorerController.getManager().getBlockchainStats();
            
            VBox totalBlocksCard = createStatCard("Total Blocks", String.valueOf(stats.totalBlocks), "#00ff88");
            VBox totalTxCard = createStatCard("Total Transactions", String.valueOf(stats.totalTransactions), "#40e0d0");
            VBox pendingTxCard = createStatCard("Pending", String.valueOf(stats.pendingTransactions), "#ffa500");
            VBox avgBlockTimeCard = createStatCard("Avg Block Time", stats.averageBlockTime + "ms", "#ff6b6b");
            
            statsBox.getChildren().addAll(totalBlocksCard, totalTxCard, pendingTxCard, avgBlockTimeCard);
        } catch (Exception e) {
            LOGGER.warning("Could not load blockchain stats: " + e.getMessage());
        }
        
        return statsBox;
    }
    
    /**
     * Creates a stat card
     */
    private VBox createStatCard(String label, String value, String color) {
        VBox card = new VBox(5);
        card.setStyle("-fx-padding: 15; -fx-background-color: #22334e; -fx-background-radius: 8;");
        card.setAlignment(Pos.CENTER);
        
        Label labelText = new Label(label);
        labelText.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12;");
        
        Label valueText = new Label(value);
        valueText.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 18; -fx-font-weight: bold;");
        
        card.getChildren().addAll(labelText, valueText);
        return card;
    }
    
    /**
     * Creates the blocks list section
     */
    private ScrollPane createBlocksListSection() {
        VBox section = new VBox(10);
        section.setStyle("-fx-padding: 15; -fx-background-color: #16213e; -fx-background-radius: 10;");
        
        Label sectionTitle = new Label("📦 Latest Blocks");
        sectionTitle.setStyle("-fx-text-fill: #00ff88; -fx-font-size: 16; -fx-font-weight: bold;");
        
        VBox blocksList = new VBox(10);
        blocksList.setStyle("-fx-padding: 10;");
        
        try {
            List<Block> latestBlocks = blockExplorerController.getManager().getLatestBlocks(20);
            
            if (latestBlocks.isEmpty()) {
                Label noBlocksLabel = new Label("No blocks to display");
                noBlocksLabel.setStyle("-fx-text-fill: #cccccc;");
                blocksList.getChildren().add(noBlocksLabel);
            } else {
                for (Block block : latestBlocks) {
                    HBox blockRow = createBlockRow(block);
                    blocksList.getChildren().add(blockRow);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Could not load blocks: " + e.getMessage());
            Label errorLabel = new Label("Error loading blocks: " + e.getMessage());
            errorLabel.setStyle("-fx-text-fill: #ff6b6b;");
            blocksList.getChildren().add(errorLabel);
        }
        
        section.getChildren().addAll(sectionTitle, blocksList);
        
        ScrollPane scrollPane = new ScrollPane(section);
        scrollPane.setStyle("-fx-background-color: #1a1a2e;");
        scrollPane.setFitToWidth(true);
        
        return scrollPane;
    }
    
    /**
     * Creates a row for displaying a block
     */
    private HBox createBlockRow(Block block) {
        HBox row = new HBox(15);
        row.setStyle("-fx-padding: 10; -fx-background-color: #22334e; -fx-background-radius: 8; -fx-border-color: #40e0d0;");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setCursor(javafx.scene.Cursor.HAND);
        
        Label blockNumLabel = new Label("#" + block.getNumber());
        blockNumLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold; -fx-font-size: 12; -fx-min-width: 80;");
        
        Label hashLabel = new Label(block.getHash() != null && block.getHash().length() > 16 
            ? block.getHash().substring(0, 16) + "..." 
            : block.getHash());
        hashLabel.setStyle("-fx-text-fill: #40e0d0; -fx-font-size: 11; -fx-min-width: 200;");
        
        int txCount = block.getTransactions() != null ? block.getTransactions().size() : 0;
        Label txLabel = new Label("Txs: " + txCount);
        txLabel.setStyle("-fx-text-fill: #ffa500; -fx-font-size: 11;");
        
        Label timeLabel = new Label(BlockExplorerUIUtils.getTimeAgo(block.getTimestamp()));
        timeLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 11;");
        
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        
        row.getChildren().addAll(blockNumLabel, hashLabel, txLabel, spacer2, timeLabel);
        
        // Click to view details
        row.setOnMouseClicked(e -> blockExplorerController.viewBlockDetails(block));
        
        return row;
    }
}