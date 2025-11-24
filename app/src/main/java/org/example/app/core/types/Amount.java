package org.example.app.core.types;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Amount represents a multi-currency amount in the TINC Blockchain system.
 * Supports multiple token types including national currencies, ASU, and export tokens.
 * 
 * This class is critical for the African Tokenized Currency System as it handles
 * amounts across different currency layers (national tokens, ASU, export tokens).
 */
public class Amount implements ByteSerializable {
    
    private final BigInteger value;
    private final String currency;
    private final int decimals; // Number of decimal places
    
    // Standard decimal places for different currency types
    public static final int FIAT_DECIMALS = 2;      // Traditional fiat: 2 decimals
    public static final int TOKEN_DECIMALS = 18;     // Blockchain tokens: 18 decimals
    public static final int COMMODITY_DECIMALS = 8;  // Commodities: 8 decimals
    
    /**
     * Creates a new Amount
     * 
     * @param value The numeric value (in smallest units)
     * @param currency Currency symbol (e.g., "NGN", "GHS", "ASU", "EXPORT-TOKEN")
     * @param decimals Number of decimal places
     */
    public Amount(BigInteger value, String currency, int decimals) {
        if (value == null) {
            throw new IllegalArgumentException("Amount value cannot be null");
        }
        if (value.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (currency == null || currency.isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be null or empty");
        }
        if (decimals < 0 || decimals > 18) {
            throw new IllegalArgumentException("Decimals must be between 0 and 18");
        }
        
        this.value = value;
        this.currency = currency.toUpperCase();
        this.decimals = decimals;
    }
    
    /**
     * Creates a new Amount with standard token decimals (18)
     */
    public Amount(BigInteger value, String currency) {
        this(value, currency, TOKEN_DECIMALS);
    }
    
    /**
     * Creates an Amount from a decimal string representation
     * 
     * @param decimalString String like "100.50"
     * @param currency Currency symbol
     * @param decimals Number of decimal places
     * @return Amount object
     */
    public static Amount fromDecimal(String decimalString, String currency, int decimals) {
        if (decimalString == null || decimalString.isEmpty()) {
            throw new IllegalArgumentException("Decimal string cannot be null or empty");
        }
        
        // Remove any whitespace
        decimalString = decimalString.trim();
        
        // Handle decimal point
        String[] parts = decimalString.split("\\.");
        String integerPart = parts[0];
        String fractionalPart = parts.length > 1 ? parts[1] : "";
        
        // Pad or truncate fractional part to match decimals
        if (fractionalPart.length() < decimals) {
            fractionalPart = String.format("%-" + decimals + "s", fractionalPart).replace(' ', '0');
        } else if (fractionalPart.length() > decimals) {
            fractionalPart = fractionalPart.substring(0, decimals);
        }
        
        // Combine into smallest units
        String fullNumber = integerPart + fractionalPart;
        BigInteger value = new BigInteger(fullNumber);
        
        return new Amount(value, currency, decimals);
    }
    
    /**
     * Creates an Amount for national currency tokens
     */
    public static Amount forNationalCurrency(BigInteger value, String currencyCode) {
        return new Amount(value, currencyCode + "-TOKEN", FIAT_DECIMALS);
    }
    
    /**
     * Creates an Amount for ASU (African Stable Unit)
     */
    public static Amount forASU(BigInteger value) {
        return new Amount(value, "ASU", TOKEN_DECIMALS);
    }
    
    /**
     * Creates an Amount for export tokens
     */
    public static Amount forExportToken(BigInteger value, String commodityType) {
        return new Amount(value, "EXPORT-" + commodityType, COMMODITY_DECIMALS);
    }
    
    /**
     * Gets the raw value in smallest units
     */
    public BigInteger getValue() {
        return value;
    }
    
    /**
     * Gets the currency symbol
     */
    public String getCurrency() {
        return currency;
    }
    
    /**
     * Gets the number of decimal places
     */
    public int getDecimals() {
        return decimals;
    }
    
    /**
     * Converts to decimal string representation
     * e.g., 12345 with 2 decimals -> "123.45"
     */
    public String toDecimalString() {
        String valueStr = value.toString();
        
        // If value is smaller than decimals, pad with zeros
        while (valueStr.length() <= decimals) {
            valueStr = "0" + valueStr;
        }
        
        int splitPoint = valueStr.length() - decimals;
        String integerPart = valueStr.substring(0, splitPoint);
        String fractionalPart = valueStr.substring(splitPoint);
        
        // Remove trailing zeros from fractional part
        fractionalPart = fractionalPart.replaceAll("0+$", "");
        
        if (fractionalPart.isEmpty()) {
            return integerPart;
        } else {
            return integerPart + "." + fractionalPart;
        }
    }
    
    /**
     * Adds two amounts (must be same currency)
     */
    public Amount add(Amount other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Cannot add different currencies: " + this.currency + " and " + other.currency
            );
        }
        if (this.decimals != other.decimals) {
            throw new IllegalArgumentException("Decimal places must match");
        }
        
        return new Amount(this.value.add(other.value), this.currency, this.decimals);
    }
    
    /**
     * Subtracts two amounts (must be same currency)
     */
    public Amount subtract(Amount other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Cannot subtract different currencies: " + this.currency + " and " + other.currency
            );
        }
        if (this.decimals != other.decimals) {
            throw new IllegalArgumentException("Decimal places must match");
        }
        
        BigInteger result = this.value.subtract(other.value);
        if (result.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Result cannot be negative");
        }
        
        return new Amount(result, this.currency, this.decimals);
    }
    
    /**
     * Multiplies amount by a factor
     */
    public Amount multiply(BigInteger factor) {
        return new Amount(this.value.multiply(factor), this.currency, this.decimals);
    }
    
    /**
     * Divides amount by a divisor
     */
    public Amount divide(BigInteger divisor) {
        return new Amount(this.value.divide(divisor), this.currency, this.decimals);
    }
    
    /**
     * Compares this amount with another (must be same currency)
     */
    public int compareTo(Amount other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Cannot compare different currencies: " + this.currency + " and " + other.currency
            );
        }
        return this.value.compareTo(other.value);
    }
    
    /**
     * Checks if this amount is zero
     */
    public boolean isZero() {
        return value.equals(BigInteger.ZERO);
    }
    
    /**
     * Checks if this amount is positive
     */
    public boolean isPositive() {
        return value.compareTo(BigInteger.ZERO) > 0;
    }
    
    /**
     * Checks if this is a national currency token
     */
    public boolean isNationalCurrency() {
        return currency.endsWith("-TOKEN") && !currency.startsWith("EXPORT-");
    }
    
    /**
     * Checks if this is an ASU amount
     */
    public boolean isASU() {
        return currency.equals("ASU");
    }
    
    /**
     * Checks if this is an export token
     */
    public boolean isExportToken() {
        return currency.startsWith("EXPORT-");
    }
    
    @Override
    public byte[] toBytes() {
        byte[] currencyBytes = currency.getBytes();
        byte[] valueBytes = value.toByteArray();
        
        ByteBuffer buffer = ByteBuffer.allocate(
            4 + currencyBytes.length +  // Currency with length prefix
            4 + valueBytes.length +      // Value with length prefix
            4                            // Decimals
        );
        
        // Write currency
        buffer.putInt(currencyBytes.length);
        buffer.put(currencyBytes);
        
        // Write value
        buffer.putInt(valueBytes.length);
        buffer.put(valueBytes);
        
        // Write decimals
        buffer.putInt(decimals);
        
        return buffer.array();
    }
    
    /**
     * Creates an Amount from bytes
     */
    public static Amount fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        
        // Read currency
        int currencyLength = buffer.getInt();
        byte[] currencyBytes = new byte[currencyLength];
        buffer.get(currencyBytes);
        String currency = new String(currencyBytes);
        
        // Read value
        int valueLength = buffer.getInt();
        byte[] valueBytes = new byte[valueLength];
        buffer.get(valueBytes);
        BigInteger value = new BigInteger(valueBytes);
        
        // Read decimals
        int decimals = buffer.getInt();
        
        return new Amount(value, currency, decimals);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Amount other = (Amount) obj;
        return decimals == other.decimals &&
               value.equals(other.value) &&
               currency.equals(other.currency);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(value, currency, decimals);
    }
    
    @Override
    public String toString() {
        return toDecimalString() + " " + currency;
    }
    
    /**
     * Creates a formatted string with full currency name
     */
    public String toFormattedString() {
        return String.format("%s %s", toDecimalString(), currency);
    }
}