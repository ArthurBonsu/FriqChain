package org.example.app.core.ui.wallet;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.regex.Pattern;

public class WalletUIUtils {
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$");
    private static final DecimalFormat BALANCE_FORMATTER = new DecimalFormat("#,##0");
    
    public static final Color PRIMARY_COLOR = new Color(0, 255, 136);
    public static final Color BACKGROUND_COLOR = new Color(26, 26, 46);
    public static final Color SECONDARY_COLOR = new Color(22, 33, 62);
    public static final Color TEXT_COLOR = Color.WHITE;
    
    public static boolean isValidAddress(String address) {
        return address != null && ADDRESS_PATTERN.matcher(address.trim()).matches();
    }
    
    public static boolean isValidAmount(String amount) {
        try {
            double value = Double.parseDouble(amount.trim());
            return value > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    public static boolean isValidGasPrice(String gasPrice) {
        try {
            long value = Long.parseLong(gasPrice.trim());
            return value > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    public static boolean isValidGasLimit(String gasLimit) {
        try {
            int value = Integer.parseInt(gasLimit.trim());
            return value >= 21000 && value <= 10_000_000;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    public static String formatBalance(long balance) {
        return BALANCE_FORMATTER.format(balance);
    }
    
    public static String formatBalance(long balance, String unit) {
        return formatBalance(balance) + " " + unit;
    }
    
    public static String formatAddress(String address) {
        if (address == null || address.length() < 10) {
            return address;
        }
        return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
    }
    
    public static String formatTimestamp(long timestamp) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(timestamp));
    }
}