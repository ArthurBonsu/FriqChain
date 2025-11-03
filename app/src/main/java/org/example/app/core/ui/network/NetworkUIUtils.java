package org.example.app.core.ui.network;

import org.example.app.core.network.Messages;
import org.example.app.core.metrics.NetworkMetrics;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.application.Platform;

import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * NetworkUIUtils provides utility functions for network-related UI operations,
 * including formatting, validation, styling, and common UI components.
 */
public class NetworkUIUtils {
    private static final Logger LOGGER = Logger.getLogger(NetworkUIUtils.class.getName());
    
    // Formatting patterns
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###");
    private static final DecimalFormat PERCENTAGE_FORMAT = new DecimalFormat("##0.0%");
    private static final DecimalFormat BYTE_FORMAT = new DecimalFormat("#,##0.0");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Validation patterns
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile(
        "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+" +
        "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$|^localhost$"
    );
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    
    // UI Color constants
    public static final String COLOR_SUCCESS = "#00ff88";
    public static final String COLOR_WARNING = "#ffa500";
    public static final String COLOR_ERROR = "#ff6b6b";
    public static final String COLOR_INFO = "#40e0d0";
    public static final String COLOR_ACCENT = "#8a2be2";
    public static final String COLOR_PRIMARY = "#0084ff";
    public static final String COLOR_SECONDARY = "#6c757d";
    
    // Network state colors
    public static final String COLOR_NETWORK_ACTIVE = "#00ff00";
    public static final String COLOR_NETWORK_CONNECTING = "#ffaa00";
    public static final String COLOR_NETWORK_ERROR = "#ff0000";
    public static final String COLOR_NETWORK_STOPPED = "#888888";
    
    // Private constructor to prevent instantiation
    private NetworkUIUtils() {
        throw new UnsupportedOperationException("NetworkUIUtils is a utility class");
    }
    
    // ================================
    // FORMATTING UTILITIES
    // ================================
    
    /**
     * Format number with thousands separators
     */
    public static String formatNumber(long number) {
        return DECIMAL_FORMAT.format(number);
    }
    
    /**
     * Format percentage value
     */
    public static String formatPercentage(double value) {
        return PERCENTAGE_FORMAT.format(value);
    }
    
    /**
     * Format byte count with appropriate units
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        
        return BYTE_FORMAT.format(value) + " " + units[unitIndex];
    }
    
    /**
     * Format data rate (bytes per second)
     */
    public static String formatDataRate(long bytesPerSecond) {
        return formatBytes(bytesPerSecond) + "/s";
    }
    
    /**
     * Format latency in milliseconds
     */
    public static String formatLatency(long latencyMs) {
        if (latencyMs < 1000) {
            return latencyMs + " ms";
        } else if (latencyMs < 60000) {
            return BYTE_FORMAT.format(latencyMs / 1000.0) + " s";
        } else {
            return BYTE_FORMAT.format(latencyMs / 60000.0) + " min";
        }
    }
    
    /**
     * Format uptime duration
     */
    public static String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes % 60, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Format timestamp
     */
    public static String formatTime(long timestamp) {
       return LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC)
                           .format(TIME_FORMATTER);
    }
    
    /**
     * Format full date and time
     */
    public static String formatDateTime(long timestamp) {
      return LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC)
                           .format(DATETIME_FORMATTER);
    }
    
    /**
     * Format network address
     */
    public static String formatAddress(InetSocketAddress address) {
        if (address == null) {
            return "Unknown";
        }
        return address.getHostString() + ":" + address.getPort();
    }
    
    /**
     * Format peer ID for display
     */
    public static String formatPeerId(String peerId) {
        if (peerId == null || peerId.length() <= 16) {
            return peerId;
        }
        return peerId.substring(0, 8) + "..." + peerId.substring(peerId.length() - 6);
    }
    
    /**
     * Format message type
     */
    public static String formatMessageType(Messages.Message message) {
        if (message == null) {
            return "Unknown";
        }
        
        String className = message.getClass().getSimpleName();
        // Remove "Message" suffix if present
        if (className.endsWith("Message")) {
            className = className.substring(0, className.length() - 7);
        }
        
        // Convert camelCase to spaced words
        return className.replaceAll("([A-Z])", " $1").trim();
    }
    
    // ================================
    // VALIDATION UTILITIES
    // ================================
    
    /**
     * Validate IP address
     */
    public static boolean isValidIPAddress(String ip) {
        return ip != null && IP_PATTERN.matcher(ip).matches();
    }
    
    /**
     * Validate hostname
     */
    public static boolean isValidHostname(String hostname) {
        return hostname != null && (HOSTNAME_PATTERN.matcher(hostname).matches() || 
                                  isValidIPAddress(hostname));
    }
    
    /**
     * Validate port number
     */
    public static boolean isValidPort(int port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }
    
    /**
     * Validate port number from string
     */
    public static boolean isValidPort(String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            return isValidPort(port);
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Validate network address format
     */
    public static boolean isValidNetworkAddress(String host, String port) {
        return isValidHostname(host) && isValidPort(port);
    }
    
    /**
     * Validate peer ID format
     */
    public static boolean isValidPeerId(String peerId) {
        return peerId != null && !peerId.trim().isEmpty() && 
               peerId.length() >= 3 && peerId.length() <= 128;
    }
    
    /**
     * Parse network address from string
     */
    public static InetSocketAddress parseNetworkAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return null;
        }
        
        String[] parts = address.split(":");
        if (parts.length != 2) {
            return null;
        }
        
        String host = parts[0].trim();
        String portStr = parts[1].trim();
        
        if (!isValidHostname(host) || !isValidPort(portStr)) {
            return null;
        }
        
        try {
            int port = Integer.parseInt(portStr);
            return new InetSocketAddress(host, port);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    // ================================
    // UI STYLING UTILITIES
    // ================================
    
    /**
     * Get color for network state
     */
    public static String getNetworkStateColor(NetworkManager.NetworkState.StateType state) {
        switch (state) {
            case ACTIVE:
                return COLOR_NETWORK_ACTIVE;
            case STARTING:
            case CONNECTING:
            case DISCOVERING:
                return COLOR_NETWORK_CONNECTING;
            case ERROR:
            case DEGRADED:
                return COLOR_NETWORK_ERROR;
            case STOPPED:
            default:
                return COLOR_NETWORK_STOPPED;
        }
    }
    
    /**
     * Get color for health level
     */
    public static String getHealthColor(double healthValue) {
        if (healthValue >= 0.8) {
            return COLOR_SUCCESS;
        } else if (healthValue >= 0.6) {
            return COLOR_INFO;
        } else if (healthValue >= 0.4) {
            return COLOR_WARNING;
        } else {
            return COLOR_ERROR;
        }
    }
    
    /**
     * Get color for connection policy
     */
    public static String getPolicyColor(NetworkManager.ConnectionPolicy.PolicyType policy) {
        switch (policy) {
            case CONSERVATIVE:
                return COLOR_WARNING;
            case BALANCED:
                return COLOR_SUCCESS;
            case AGGRESSIVE:
                return COLOR_PRIMARY;
            case CUSTOM:
                return COLOR_ACCENT;
            default:
                return COLOR_SECONDARY;
        }
    }
    
    /**
     * Apply status styling to label
     */
    public static void applyStatusStyle(Label label, String status, String color) {
        if (label != null) {
            label.setText(status);
            label.setStyle(String.format("-fx-text-fill: %s; -fx-font-weight: bold;", color));
        }
    }
    
    /**
     * Apply metric card styling
     */
    public static String getMetricCardStyle() {
        return "-fx-background-color: #22334e; -fx-background-radius: 12; " +
               "-fx-padding: 25; -fx-pref-width: 200; -fx-alignment: center;";
    }
    
    /**
     * Apply input field styling
     */
    public static String getInputFieldStyle() {
        return "-fx-background-color: #16213e; -fx-text-fill: white; " +
               "-fx-border-color: #40e0d0; -fx-border-radius: 6; -fx-padding: 8;";
    }
    
    /**
     * Apply button styling
     */
    public static String getButtonStyle(String color, boolean filled) {
        if (filled) {
            return String.format("-fx-background-color: %s; -fx-text-fill: white; " +
                               "-fx-font-weight: bold; -fx-padding: 10 20; " +
                               "-fx-background-radius: 8; -fx-font-size: 14px;", color);
        } else {
            return String.format("-fx-background-color: transparent; -fx-text-fill: %s; " +
                               "-fx-border-color: %s; -fx-border-width: 2; " +
                               "-fx-font-weight: bold; -fx-padding: 8 16; " +
                               "-fx-background-radius: 6; -fx-font-size: 14px;", color, color);
        }
    }
    
    /**
     * Apply container styling
     */
    public static String getContainerStyle() {
        return "-fx-background-color: #16213e; -fx-padding: 25;";
    }
    
    /**
     * Apply section styling
     */
    public static String getSectionStyle() {
        return "-fx-background-color: #22334e; -fx-background-radius: 8; -fx-padding: 20;";
    }
    
    // ================================
    // UI COMPONENT CREATORS
    // ================================
    
    /**
     * Create metric display card
     */
    public static VBox createMetricCard(String title, String value, String color) {
        VBox card = new VBox(10);
        card.setStyle(getMetricCardStyle());
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 14px; -fx-alignment: center;");
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 28px; " +
                                        "-fx-font-weight: bold; -fx-alignment: center;", color));
        
        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }
    
    /**
     * Create status indicator
     */
    public static HBox createStatusIndicator(String label, String status, String color) {
        HBox container = new HBox(10);
        container.setStyle("-fx-alignment: center-left; -fx-padding: 5 0;");
        
        Label statusLabel = new Label(label + ":");
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        
        Label valueLabel = new Label(status);
        valueLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-weight: bold;", color));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        container.getChildren().addAll(statusLabel, spacer, valueLabel);
        return container;
    }
    
    /**
     * Create peer connection row
     */
    public static HBox createPeerRow(NetworkController.PeerInfo peer) {
        HBox row = new HBox(15);
        row.setStyle("-fx-alignment: center-left; -fx-padding: 10; " +
                    "-fx-background-color: #1a2332; -fx-background-radius: 6;");
        
        // Peer ID
        Label peerLabel = new Label(formatPeerId(peer.peerId));
        peerLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-min-width: 120;");
        
        // Address
        Label addressLabel = new Label(formatAddress(peer.address));
        addressLabel.setStyle("-fx-text-fill: #ccc; -fx-min-width: 150;");
        
        // Status
        String statusColor = peer.connected ? COLOR_SUCCESS : COLOR_ERROR;
        Label statusLabel = new Label(peer.status);
        statusLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-weight: bold; -fx-min-width: 80;", 
                                          statusColor));
        
        // Latency
        Label latencyLabel = new Label(formatLatency(peer.latency));
        latencyLabel.setStyle("-fx-text-fill: #40e0d0; -fx-min-width: 80;");
        
        row.getChildren().addAll(peerLabel, addressLabel, statusLabel, latencyLabel);
        return row;
    }
    
    /**
     * Create health progress bar
     */
    public static HBox createHealthBar(String label, double value) {
        HBox container = new HBox(10);
        container.setStyle("-fx-alignment: center-left; -fx-padding: 5 0;");
        
        Label nameLabel = new Label(label);
        nameLabel.setStyle("-fx-text-fill: white; -fx-min-width: 100;");
        
        // Simple progress bar simulation using labels
        String progressColor = getHealthColor(value);
        int progressWidth = (int) (value * 200); // 200px max width
        
        Label progressBar = new Label();
        progressBar.setStyle(String.format(
            "-fx-background-color: %s; -fx-min-width: %dpx; -fx-max-width: %dpx; " +
            "-fx-min-height: 20px; -fx-background-radius: 10;", 
            progressColor, progressWidth, progressWidth));
        
        Label valueLabel = new Label(formatPercentage(value));
        valueLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-weight: bold;", progressColor));
        
        container.getChildren().addAll(nameLabel, progressBar, valueLabel);
        return container;
    }
    
    // ================================
    // DIALOG AND ALERT UTILITIES
    // ================================
    
    /**
     * Show error alert
     */
    public static void showErrorAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Show warning alert
     */
    public static void showWarningAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Show info alert
     */
    public static void showInfoAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Show confirmation dialog
     */
    public static boolean showConfirmationDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        return alert.showAndWait()
                   .filter(response -> response == ButtonType.OK)
                   .isPresent();
    }
    
    /**
     * Show peer connection dialog
     */
    public static InetSocketAddress showPeerConnectionDialog() {
        Alert dialog = new Alert(Alert.AlertType.NONE);
        dialog.setTitle("Connect to Peer");
        dialog.setHeaderText("Enter peer connection details");
        
        VBox content = new VBox(15);
        content.setStyle("-fx-padding: 20;");
        
        TextField hostField = new TextField();
        hostField.setPromptText("Host (IP address or hostname)");
        hostField.setStyle(getInputFieldStyle());
        
        TextField portField = new TextField("8080");
        portField.setPromptText("Port number");
        portField.setStyle(getInputFieldStyle());
        
        Label hostLabel = new Label("Host:");
        hostLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        
        Label portLabel = new Label("Port:");
        portLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        
        content.getChildren().addAll(hostLabel, hostField, portLabel, portField);
        dialog.getDialogPane().setContent(content);
        
        ButtonType connectButton = new ButtonType("Connect");
        ButtonType cancelButton = new ButtonType("Cancel");
        dialog.getButtonTypes().setAll(connectButton, cancelButton);
        
        return dialog.showAndWait()
                    .filter(response -> response == connectButton)
                    .map(response -> {
                        String host = hostField.getText().trim();
                        String port = portField.getText().trim();
                        
                        if (isValidNetworkAddress(host, port)) {
                            return new InetSocketAddress(host, Integer.parseInt(port));
                        } else {
                            showErrorAlert("Invalid Address", 
                                         "Please enter a valid host and port number.");
                            return null;
                        }
                    })
                    .orElse(null);
    }
    
    /**
     * Show network configuration dialog
     */
    public static NetworkManager.NetworkSettings showNetworkSettingsDialog(NetworkManager.NetworkSettings currentSettings) {
        Alert dialog = new Alert(Alert.AlertType.NONE);
        dialog.setTitle("Network Settings");
        dialog.setHeaderText("Configure network parameters");
        
        VBox content = new VBox(15);
        content.setStyle("-fx-padding: 20;");
        
        // Discovery interval
        TextField discoveryField = new TextField(String.valueOf(currentSettings.getDiscoveryInterval()));
        discoveryField.setPromptText("Discovery interval (ms)");
        discoveryField.setStyle(getInputFieldStyle());
        
        // Health check interval
        TextField healthField = new TextField(String.valueOf(currentSettings.getHealthCheckInterval()));
        healthField.setPromptText("Health check interval (ms)");
        healthField.setStyle(getInputFieldStyle());
        
        // Connection timeout
        TextField timeoutField = new TextField(String.valueOf(currentSettings.getConnectionTimeout()));
        timeoutField.setPromptText("Connection timeout (ms)");
        timeoutField.setStyle(getInputFieldStyle());
        
        content.getChildren().addAll(
            new Label("Discovery Interval (ms):") {{ setStyle("-fx-text-fill: white; -fx-font-weight: bold;"); }},
            discoveryField,
            new Label("Health Check Interval (ms):") {{ setStyle("-fx-text-fill: white; -fx-font-weight: bold;"); }},
            healthField,
            new Label("Connection Timeout (ms):") {{ setStyle("-fx-text-fill: white; -fx-font-weight: bold;"); }},
            timeoutField
        );
        
        dialog.getDialogPane().setContent(content);
        dialog.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        
        return dialog.showAndWait()
                    .filter(response -> response == ButtonType.OK)
                    .map(response -> {
                        try {
                            NetworkManager.NetworkSettings newSettings = new NetworkManager.NetworkSettings();
                            newSettings.setDiscoveryInterval(Integer.parseInt(discoveryField.getText()));
                            newSettings.setHealthCheckInterval(Integer.parseInt(healthField.getText()));
                            newSettings.setConnectionTimeout(Integer.parseInt(timeoutField.getText()));
                            
                            // Copy other settings
                            newSettings.setMaxRetries(currentSettings.getMaxRetries());
                            newSettings.setAutoOptimize(currentSettings.isAutoOptimize());
                            newSettings.setLogNetworkEvents(currentSettings.isLogNetworkEvents());
                            
                            return newSettings;
                        } catch (NumberFormatException e) {
                            showErrorAlert("Invalid Settings", "Please enter valid numeric values.");
                            return null;
                        }
                    })
                    .orElse(null);
    }
    
    // ================================
    // UTILITY FUNCTIONS
    // ================================
    
    /**
     * Get network statistics summary
     */
    public static String getNetworkStatsSummary(NetworkMetrics metrics) {
        return String.format(
            "Messages: %s sent, %s received | Data: %s sent, %s received | Peers: %d connected",
            formatNumber(metrics.getMessagesSent()),
            formatNumber(metrics.getMessagesReceived()),
            formatBytes(metrics.getBytesSent()),
            formatBytes(metrics.getBytesReceived()),
            metrics.getTotalPeers()
        );
    }
    
    /**
     * Get connection policy description
     */
    public static String getPolicyDescription(NetworkManager.ConnectionPolicy policy) {
        return String.format(
            "%s policy - Connections: %d-%d, Discovery: %s, Trusted peers: %d, Blocked peers: %d",
            policy.getPolicyType().getDisplayName(),
            policy.getMinConnections(),
            policy.getMaxConnections(),
            policy.isEnableDiscovery() ? "Enabled" : "Disabled",
            policy.getTrustedPeers().size(),
            policy.getBlockedPeers().size()
        );
    }
    
    /**
     * Calculate network efficiency score
     */
    public static double calculateNetworkEfficiency(NetworkMetrics metrics, int connectedPeers) {
        if (connectedPeers == 0) {
            return 0.0;
        }
        
        long totalMessages = metrics.getMessagesSent() + metrics.getMessagesReceived();
        if (totalMessages == 0) {
            return 0.5; // Neutral score for new networks
        }
        
        // Simple efficiency calculation based on message throughput per peer
        double messagesPerPeer = (double) totalMessages / connectedPeers;
        
        // Normalize to 0-1 range (assuming 100 messages per peer is optimal)
        return Math.min(1.0, messagesPerPeer / 100.0);
    }
    
    /**
     * Get recommended connection policy based on network conditions
     */
    public static NetworkManager.ConnectionPolicy.PolicyType getRecommendedPolicy(
            int currentPeers, NetworkManager.NetworkHealthStatus health) {
        
        if (health.overallHealth < 0.3) {
            return NetworkManager.ConnectionPolicy.PolicyType.AGGRESSIVE;
        } else if (currentPeers < 5) {
            return NetworkManager.ConnectionPolicy.PolicyType.BALANCED;
        } else if (currentPeers > 50) {
            return NetworkManager.ConnectionPolicy.PolicyType.CONSERVATIVE;
        } else {
            return NetworkManager.ConnectionPolicy.PolicyType.BALANCED;
        }
    }
    
    /**
     * Generate network health report
     */
    public static String generateHealthReport(NetworkManager.NetworkHealthStatus health) {
        StringBuilder report = new StringBuilder();
        
        report.append("=== NETWORK HEALTH REPORT ===\n\n");
        report.append(String.format("Overall Health: %s (%s)\n", 
                                   formatPercentage(health.overallHealth), 
                                   health.getHealthLevel()));
        report.append(String.format("Connectivity: %s\n", formatPercentage(health.connectivityHealth)));
        report.append(String.format("Performance: %s\n", formatPercentage(health.performanceHealth)));
        report.append(String.format("Stability: %s\n", formatPercentage(health.stabilityHealth)));
        report.append(String.format("\nConnected Peers: %d / %d target\n", 
                                   health.connectedPeers, health.targetPeers));
        report.append(String.format("Average Latency: %s\n", formatLatency(health.avgLatency)));
        report.append(String.format("Message Success Rate: %s\n", formatPercentage(health.messageSuccessRate)));
        report.append(String.format("\nStatus: %s\n", health.healthMessage));
        
        return report.toString();
    }
    
    /**
     * Log network event with formatting
     */
    public static void logNetworkEvent(String event, Object... params) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String message = String.format("[%s] NETWORK: %s", timestamp, String.format(event, params));
        LOGGER.info(message);
    }
    
    /**
     * Safe thread execution for UI updates
     */
    public static void runOnUIThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
    
    /**
     * Truncate text for display
     */
    public static String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}