package org.example.app.core.ui.blockexplorer;

import java.awt.Color;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BlockExplorerUIUtils {
    private static final DecimalFormat HASH_FORMATTER = new DecimalFormat("#");
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public static final Color PRIMARY_COLOR = new Color(30, 144, 255);
    public static final Color BACKGROUND_COLOR = new Color(26, 26, 46);
    public static final Color SECONDARY_COLOR = new Color(22, 33, 62);
    public static final Color TEXT_COLOR = Color.WHITE;
    public static final Color SUCCESS_COLOR = new Color(0, 255, 136);
    public static final Color WARNING_COLOR = new Color(255, 192, 0);
    public static final Color ERROR_COLOR = new Color(255, 77, 77);
    
    /**
     * Formats a block hash for display (shortened version)
     */
    public static String formatBlockHash(String hash) {
        if (hash == null || hash.length() < 10) {
            return hash;
        }
        return hash.substring(0, 8) + "..." + hash.substring(hash.length() - 8);
    }
    
    /**
     * Formats a transaction hash for display (shortened version)
     */
    public static String formatTxHash(String hash) {
        if (hash == null || hash.length() < 10) {
            return hash;
        }
        return hash.substring(0, 8) + "..." + hash.substring(hash.length() - 8);
    }
    
    /**
     * Formats an address for display (shortened version)
     */
    public static String formatAddress(String address) {
        if (address == null || address.length() < 10) {
            return address;
        }
        return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
    }
    
    /**
     * Formats a timestamp for display
     */
    public static String formatTimestamp(long timestamp) {
        try {
            return DATE_FORMATTER.format(new Date(timestamp));
        } catch (Exception e) {
            return "Invalid timestamp";
        }
    }
    
    /**
     * Formats block difficulty for display
     */
    public static String formatDifficulty(long difficulty) {
        return String.format("%,d", difficulty);
    }
    
    /**
     * Formats block number for display
     */
    public static String formatBlockNumber(long number) {
        return String.format("#%,d", number);
    }
    
    /**
     * Calculates time elapsed since timestamp
     */
    public static String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "") + " ago";
        }
    }
    
    /**
     * Validates a block hash format
     */
    public static boolean isValidBlockHash(String hash) {
        return hash != null && !hash.trim().isEmpty() && hash.length() >= 8;
    }
    
    /**
     * Validates a block number
     */
    public static boolean isValidBlockNumber(long number) {
        return number >= 0;
    }
    
    /**
     * Validates transaction hash format
     */
    public static boolean isValidTxHash(String hash) {
        return hash != null && !hash.trim().isEmpty() && hash.length() >= 8;
    }
    
    /**
     * Gets color based on transaction status
     */
    public static Color getStatusColor(String status) {
        if (status == null) {
            return ERROR_COLOR;
        }
        
        switch (status.toLowerCase()) {
            case "confirmed":
            case "success":
                return SUCCESS_COLOR;
            case "pending":
                return WARNING_COLOR;
            case "failed":
            case "error":
                return ERROR_COLOR;
            default:
                return TEXT_COLOR;
        }
    }
    
    /**
     * Formats transaction count for display
     */
    public static String formatTransactionCount(int count) {
        return String.format("%,d", count);
    }
    
    /**
     * Formats gas limit for display
     */
    public static String formatGasLimit(long gasLimit) {
        return String.format("%,d", gasLimit);
    }
    
    /**
     * Formats gas price for display
     */
    public static String formatGasPrice(long gasPrice) {
        return String.format("%,d", gasPrice);
    }
    
    /**
     * Truncates text to a specified length with ellipsis
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