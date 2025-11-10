package org.example.app.core.blockchain.transaction;

import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CurrencyExchangeTransaction represents currency exchange operations in the TINC Blockchain
 * global banking system. This transaction handles the exchange of tokenized currencies with
 * fair weighting algorithms to prevent manipulation.
 */
public class CurrencyExchangeTransaction extends Transaction {
    
    // Constants for fair rate calculation
    private static final int PRECISION = 18; // Decimal precision for calculations
    private static final MathContext MC = new MathContext(PRECISION, RoundingMode.HALF_UP);
    
    // Weight factors for different exchange sources (sum should be 1.0)
    private static final Map<String, Double> EXCHANGE_WEIGHTS = new HashMap<>();
    static {
        // African exchanges get higher weights to prioritize local markets
        EXCHANGE_WEIGHTS.put("AfriEX", 0.20);
        EXCHANGE_WEIGHTS.put("NairaTrade", 0.15);
        EXCHANGE_WEIGHTS.put("AfriCrypt", 0.15);
        EXCHANGE_WEIGHTS.put("Binance", 0.10);
        EXCHANGE_WEIGHTS.put("Coinbase", 0.10);
        EXCHANGE_WEIGHTS.put("Kraken", 0.10);
        EXCHANGE_WEIGHTS.put("LocalEx", 0.10);
        EXCHANGE_WEIGHTS.put("UEX", 0.05);
        EXCHANGE_WEIGHTS.put("GlobalMarket", 0.05);
    }
    
    // Outlier detection thresholds
    private static final double OUTLIER_THRESHOLD = 0.15; // 15% from median
    private static final double MIN_SOURCES_RATIO = 0.6; // Must have at least 60% of sources valid
    
    // Rate stability factors
    private static final double VOLATILITY_THRESHOLD = 0.05; // 5% change requires additional verification
    private static final long RATE_CACHE_EXPIRY_MS = 3600000; // 1 hour
    
    // Volume thresholds for weighted calculations (in USD equivalent)
    private static final BigDecimal MIN_VOLUME = new BigDecimal("10000"); // $10,000 minimum for consideration
    private static final BigDecimal VOLUME_CAP = new BigDecimal("10000000"); // $10M cap to prevent manipulation
    
    // Static rate cache with expiration
    private static final Map<String, RateEntry> RATE_CACHE = new HashMap<>();
    
    private final String sourceTokenSymbol;
    private final String targetTokenSymbol;
    private final BigInteger sourceAmount;
    private final BigInteger targetAmount;
    private final BigInteger exchangeRate;
    private final String exchangeId;
    private final Date exchangeDate;
    private final Address userAddress;
    private final Address bankAddress;
    private final List<String> exchangeSources; // Sources used for rate calculation
    private final byte[] fairWeightingProof; // Proof of fair weighting algorithm
    private final List<BigInteger> sourceRates; // Individual rates from each source
    private final List<BigInteger> sourceVolumes; // Volumes for each source (for weighting)
    private final List<Integer> trustScores; // Trust scores for each source (1-100)
    
    // Static class for rate caching
    private static class RateEntry {
        BigDecimal rate;
        long timestamp;
        
        RateEntry(BigDecimal rate) {
            this.rate = rate;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > RATE_CACHE_EXPIRY_MS;
        }
    }
    
    /**
     * Creates a new CurrencyExchangeTransaction
     * 
     * @param sourceTokenSymbol Source token symbol
     * @param targetTokenSymbol Target token symbol
     * @param sourceAmount Amount of source tokens
     * @param targetAmount Amount of target tokens
     * @param exchangeRate Exchange rate used
     * @param userAddress User's address
     * @param bankAddress Bank's address
     * @param exchangeSources List of exchange sources used for rate
     * @param fairWeightingProof Proof of fair weighting algorithm
     * @param sourceRates Individual rates from each source
     * @param sourceVolumes Volumes for each source (for weighting)
     * @param trustScores Trust scores for each source (1-100)
     */
    public CurrencyExchangeTransaction(
            String sourceTokenSymbol, String targetTokenSymbol,
            BigInteger sourceAmount, BigInteger targetAmount,
            BigInteger exchangeRate, Address userAddress,
            Address bankAddress, List<String> exchangeSources,
            byte[] fairWeightingProof, List<BigInteger> sourceRates,
            List<BigInteger> sourceVolumes, List<Integer> trustScores) {
            
        super(buildTransactionData(sourceTokenSymbol, targetTokenSymbol, sourceAmount,
                targetAmount, exchangeRate, userAddress, bankAddress,
                exchangeSources, fairWeightingProof, sourceRates, sourceVolumes, trustScores));
                
        this.sourceTokenSymbol = sourceTokenSymbol;
        this.targetTokenSymbol = targetTokenSymbol;
        this.sourceAmount = sourceAmount;
        this.targetAmount = targetAmount;
        this.exchangeRate = exchangeRate;
        this.exchangeId = UUID.randomUUID().toString();
        this.exchangeDate = new Date();
        this.userAddress = userAddress;
        this.bankAddress = bankAddress;
        this.exchangeSources = new ArrayList<>(exchangeSources);
        this.fairWeightingProof = fairWeightingProof.clone();
        this.sourceRates = new ArrayList<>(sourceRates);
        this.sourceVolumes = new ArrayList<>(sourceVolumes);
        this.trustScores = new ArrayList<>(trustScores);
        
        // Update rate cache
        updateRateCache(sourceTokenSymbol, targetTokenSymbol, exchangeRate);
    }
    
    /**
     * Creates a simple CurrencyExchangeTransaction (backward compatibility)
     */
    public CurrencyExchangeTransaction(String sourceTokenSymbol, String targetTokenSymbol,
                                     BigInteger sourceAmount, BigInteger targetAmount,
                                     BigInteger exchangeRate, Address userAddress,
                                     Address bankAddress, List<String> exchangeSources,
                                     byte[] fairWeightingProof, List<BigInteger> sourceRates) {
        this(sourceTokenSymbol, targetTokenSymbol, sourceAmount, targetAmount,
             exchangeRate, userAddress, bankAddress, exchangeSources,
             fairWeightingProof, sourceRates, 
             Collections.nCopies(sourceRates.size(), BigInteger.valueOf(1000000)), // Default volumes
             Collections.nCopies(sourceRates.size(), 50)); // Default trust scores
    }
    
    /**
     * Builds the transaction data byte array
     */
    private static byte[] buildTransactionData(
            String sourceTokenSymbol, String targetTokenSymbol,
            BigInteger sourceAmount, BigInteger targetAmount,
            BigInteger exchangeRate, Address userAddress,
            Address bankAddress, List<String> exchangeSources,
            byte[] fairWeightingProof, List<BigInteger> sourceRates,
            List<BigInteger> sourceVolumes, List<Integer> trustScores) {
            
        ByteBuffer buffer = ByteBuffer.allocate(8192); // Larger buffer for additional data
        
        // Write source token symbol with length prefix
        byte[] sourceSymbolBytes = sourceTokenSymbol.getBytes();
        buffer.putInt(sourceSymbolBytes.length);
        buffer.put(sourceSymbolBytes);
        
        // Write target token symbol with length prefix
        byte[] targetSymbolBytes = targetTokenSymbol.getBytes();
        buffer.putInt(targetSymbolBytes.length);
        buffer.put(targetSymbolBytes);
        
        // Write source amount
        byte[] sourceAmountBytes = sourceAmount.toByteArray();
        buffer.putInt(sourceAmountBytes.length);
        buffer.put(sourceAmountBytes);
        
        // Write target amount
        byte[] targetAmountBytes = targetAmount.toByteArray();
        buffer.putInt(targetAmountBytes.length);
        buffer.put(targetAmountBytes);
        
        // Write exchange rate
        byte[] rateBytes = exchangeRate.toByteArray();
        buffer.putInt(rateBytes.length);
        buffer.put(rateBytes);
        
        // Write user address
        if (userAddress != null) {
            buffer.put(userAddress.toSlice());
        } else {
            buffer.put(new byte[Address.ADDRESS_LENGTH]);
        }
        
        // Write bank address
        if (bankAddress != null) {
            buffer.put(bankAddress.toSlice());
        } else {
            buffer.put(new byte[Address.ADDRESS_LENGTH]);
        }
        
        // Write exchange sources
        buffer.putInt(exchangeSources.size());
        for (String source : exchangeSources) {
            byte[] sourceBytes = source.getBytes();
            buffer.putInt(sourceBytes.length);
            buffer.put(sourceBytes);
        }
        
        // Write fair weighting proof with length prefix
        buffer.putInt(fairWeightingProof.length);
        buffer.put(fairWeightingProof);
        
        // Write source rates
        buffer.putInt(sourceRates.size());
        for (BigInteger rate : sourceRates) {
            byte[] rateData = rate.toByteArray();
            buffer.putInt(rateData.length);
            buffer.put(rateData);
        }
        
        // Write source volumes
        buffer.putInt(sourceVolumes.size());
        for (BigInteger volume : sourceVolumes) {
            byte[] volumeData = volume.toByteArray();
            buffer.putInt(volumeData.length);
            buffer.put(volumeData);
        }
        
        // Write trust scores
        buffer.putInt(trustScores.size());
        for (Integer score : trustScores) {
            buffer.putInt(score);
        }
        
        // Finalize and return
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    // Basic getter methods preserved from original implementation
    public String getSourceTokenSymbol() {
        return sourceTokenSymbol;
    }
    
    public String getTargetTokenSymbol() {
        return targetTokenSymbol;
    }
    
    public BigInteger getSourceAmount() {
        return sourceAmount;
    }
    
    public BigInteger getTargetAmount() {
        return targetAmount;
    }
    
    public BigInteger getExchangeRate() {
        return exchangeRate;
    }
    
    public String getExchangeId() {
        return exchangeId;
    }
    
    public Date getExchangeDate() {
        return exchangeDate;
    }
    
    public Address getUserAddress() {
        return userAddress;
    }
    
    public Address getBankAddress() {
        return bankAddress;
    }
    
    public List<String> getExchangeSources() {
        return new ArrayList<>(exchangeSources);
    }
    
    public byte[] getFairWeightingProof() {
        return fairWeightingProof.clone();
    }
    
    public List<BigInteger> getSourceRates() {
        return new ArrayList<>(sourceRates);
    }
    
    /**
     * Gets the source volumes
     */
    public List<BigInteger> getSourceVolumes() {
        return new ArrayList<>(sourceVolumes);
    }
    
    /**
     * Gets the trust scores
     */
    public List<Integer> getTrustScores() {
        return new ArrayList<>(trustScores);
    }
    
    /**
     * Updates the rate cache with a new rate
     */
    private static void updateRateCache(String sourceToken, String targetToken, BigInteger rate) {
        String cacheKey = sourceToken + ":" + targetToken;
        RATE_CACHE.put(cacheKey, new RateEntry(new BigDecimal(rate)));
    }
    
    /**
     * Gets a cached rate if available and not expired
     */
    private static Optional<BigDecimal> getCachedRate(String sourceToken, String targetToken) {
        String cacheKey = sourceToken + ":" + targetToken;
        RateEntry entry = RATE_CACHE.get(cacheKey);
        
        if (entry != null && !entry.isExpired()) {
            return Optional.of(entry.rate);
        }
        
        return Optional.empty();
    }
    
    /**
     * Verifies that the exchange rate is fair using a sophisticated fair weighting algorithm
     * 
     * This implementation:
     * 1. Detects and removes outliers using median absolute deviation (MAD)
     * 2. Weights remaining rates by:
     *    - Exchange reputation/trust score (higher trust = higher weight)
     *    - Trading volume (higher volume = higher weight, but capped to prevent manipulation)
     *    - Exchange category (African exchanges get priority for African currencies)
     * 3. Compares with historical rates to detect sudden manipulative changes
     * 4. Requires minimum number of valid sources
     */
    public boolean verifyFairRate() {
        // Need minimum number of sources for reliability
        if (exchangeSources.size() < 3) {
            return false;
        }
        
        // Check data consistency
        if (exchangeSources.size() != sourceRates.size() || 
            exchangeSources.size() != sourceVolumes.size() || 
            exchangeSources.size() != trustScores.size()) {
            return false;
        }
        
        try {
            // 1. Convert BigIntegers to BigDecimal for precise calculations
            List<BigDecimal> rates = sourceRates.stream()
                .map(BigDecimal::new)
                .collect(Collectors.toList());
                
            // 2. Detect and remove outliers using Median Absolute Deviation
            List<Integer> outlierIndices = detectOutliersMAD(rates);
            
            // Ensure we have enough valid sources after removing outliers
            int minRequiredSources = (int) Math.ceil(exchangeSources.size() * MIN_SOURCES_RATIO);
            if (exchangeSources.size() - outlierIndices.size() < minRequiredSources) {
                return false; // Too many outliers, potential manipulation
            }
            
            // 3. Calculate weighted average of non-outlier rates
            BigDecimal weightedRate = calculateWeightedExchangeRate(rates, outlierIndices);
            
            // 4. Convert the provided exchange rate to BigDecimal
            BigDecimal providedRate = new BigDecimal(exchangeRate);
            
            // 5. Check if provided rate is within acceptable range of calculated rate (2%)
            BigDecimal lowerBound = weightedRate.multiply(new BigDecimal("0.98"), MC);
            BigDecimal upperBound = weightedRate.multiply(new BigDecimal("1.02"), MC);
            
            boolean isWithinBounds = providedRate.compareTo(lowerBound) >= 0 && 
                                   providedRate.compareTo(upperBound) <= 0;
            
            // 6. Check for sudden rate changes compared to historical data
            Optional<BigDecimal> cachedRate = getCachedRate(sourceTokenSymbol, targetTokenSymbol);
            if (cachedRate.isPresent()) {
                BigDecimal previousRate = cachedRate.get();
                BigDecimal changeRatio = providedRate.divide(previousRate, MC)
                                        .subtract(BigDecimal.ONE, MC).abs();
                
                // If significant change, require more validation
                if (changeRatio.compareTo(new BigDecimal(VOLATILITY_THRESHOLD)) > 0) {
                    // For significant changes, ensure we have more sources and tighter bounds
                    if (exchangeSources.size() - outlierIndices.size() < minRequiredSources + 2) {
                        return false;
                    }
                    
                    // Use tighter bounds (1% instead of 2%)
                    lowerBound = weightedRate.multiply(new BigDecimal("0.99"), MC);
                    upperBound = weightedRate.multiply(new BigDecimal("1.01"), MC);
                    
                    isWithinBounds = providedRate.compareTo(lowerBound) >= 0 && 
                                   providedRate.compareTo(upperBound) <= 0;
                }
            }
            
            return isWithinBounds;
            
        } catch (Exception e) {
            // Log error in production
            return false;
        }
    }
    
    /**
     * Calculates a weighted exchange rate based on multiple factors
     */
    private BigDecimal calculateWeightedExchangeRate(List<BigDecimal> rates, List<Integer> outlierIndices) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        for (int i = 0; i < rates.size(); i++) {
            // Skip outliers
            if (outlierIndices.contains(i)) {
                continue;
            }
            
            BigDecimal rate = rates.get(i);
            String source = exchangeSources.get(i);
            BigDecimal volume = new BigDecimal(sourceVolumes.get(i));
            int trustScore = trustScores.get(i);
            
            // Calculate composite weight based on multiple factors
            
            // 1. Trust score weight (0.0-1.0)
            BigDecimal trustWeight = new BigDecimal(trustScore).divide(new BigDecimal(100), MC);
            
            // 2. Volume weight (capped to prevent manipulation)
            BigDecimal volumeWeight;
            if (volume.compareTo(MIN_VOLUME) < 0) {
                volumeWeight = BigDecimal.ZERO; // Too little volume
            } else if (volume.compareTo(VOLUME_CAP) > 0) {
                volumeWeight = BigDecimal.ONE; // Cap the weight
            } else {
                // Linear scale between MIN_VOLUME and VOLUME_CAP
                volumeWeight = volume.subtract(MIN_VOLUME)
                              .divide(VOLUME_CAP.subtract(MIN_VOLUME), MC);
            }
            
            // 3. Exchange source weight (from predefined weights)
            BigDecimal exchangeWeight = BigDecimal.valueOf(EXCHANGE_WEIGHTS.getOrDefault(source, 0.05));
            
            // Combine weights (geometric mean to prevent any single factor from dominating)
            BigDecimal compositeWeight = BigDecimal.ONE;
            compositeWeight = compositeWeight.multiply(trustWeight, MC);
            compositeWeight = compositeWeight.multiply(volumeWeight.add(new BigDecimal("0.2"), MC), MC);
            compositeWeight = compositeWeight.multiply(exchangeWeight.multiply(new BigDecimal("5"), MC), MC);
            
            // Apply regional preference for African currencies
            if (isAfricanCurrency(sourceTokenSymbol) && isAfricanExchange(source)) {
                compositeWeight = compositeWeight.multiply(new BigDecimal("1.5"), MC);
            }
            
            // Add to weighted sum
            weightedSum = weightedSum.add(rate.multiply(compositeWeight, MC));
            totalWeight = totalWeight.add(compositeWeight);
        }
        
        // Avoid division by zero
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            // No valid rates after filtering, use median as fallback
            return getMedian(rates);
        }
        
        return weightedSum.divide(totalWeight, MC);
    }
    
    /**
     * Checks if a currency is African
     */
    private boolean isAfricanCurrency(String currencyCode) {
        // List of African currency codes (not exhaustive)
        String[] africanCurrencies = {
            "NGN", "GHS", "KES", "ZAR", "EGP", "MAD", "TZS", "UGX", "XOF", "XAF",
            "NAIRA", "CEDI", "KSHILLING", "RAND", "AFRICOIN", "ASUETOKEN", "SATOKEN"
        };
        
        for (String currency : africanCurrencies) {
            if (currencyCode.toUpperCase().contains(currency)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if an exchange is African
     */
    private boolean isAfricanExchange(String exchange) {
        // List of African exchanges (sample)
        String[] africanExchanges = {
            "AFRIEX", "NAIRATRADE", "AFRICRYPT", "LOCALEX", "AFREXCHANGE",
            "NGNTRADE", "CEDIPLAZA", "RANDMARKET"
        };
        
        for (String africanEx : africanExchanges) {
            if (exchange.toUpperCase().contains(africanEx)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Detects outliers using Median Absolute Deviation method,
     * which is more robust than standard deviation for non-normal distributions
     * and manipulated data
     */
    private List<Integer> detectOutliersMAD(List<BigDecimal> rates) {
        List<Integer> outlierIndices = new ArrayList<>();
        
        // 1. Calculate median
        BigDecimal median = getMedian(rates);
        
        // 2. Calculate absolute deviations from median
        List<BigDecimal> deviations = new ArrayList<>();
        for (BigDecimal rate : rates) {
            deviations.add(rate.subtract(median, MC).abs());
        }
        
        // 3. Calculate median of absolute deviations (MAD)
        BigDecimal mad = getMedian(deviations);
        
        // Handle case where MAD is zero (all values are equal)
        if (mad.compareTo(BigDecimal.ZERO) == 0) {
            return outlierIndices; // No outliers
        }
        
        // 4. Calculate modified Z-score and identify outliers
        // Z-score threshold of 3.5 is commonly used for outlier detection
        BigDecimal madConstant = new BigDecimal("1.4826"); // Constant for normal distribution
        for (int i = 0; i < rates.size(); i++) {
            BigDecimal modifiedZScore = deviations.get(i)
                                      .divide(mad, MC)
                                      .multiply(madConstant, MC);
            
            if (modifiedZScore.compareTo(new BigDecimal("3.5")) > 0) {
                outlierIndices.add(i);
            }
        }
        
        return outlierIndices;
    }
    
    /**
     * Calculates the median of a list of BigDecimal values
     */
    private BigDecimal getMedian(List<BigDecimal> values) {
        List<BigDecimal> sortedValues = new ArrayList<>(values);
        sortedValues.sort(BigDecimal::compareTo);
        
        int size = sortedValues.size();
        if (size == 0) {
            return BigDecimal.ZERO;
        }
        
        if (size % 2 == 1) {
            return sortedValues.get(size / 2);
        } else {
            return sortedValues.get(size / 2 - 1)
                  .add(sortedValues.get(size / 2))
                  .divide(new BigDecimal(2), MC);
        }
    }
    
    /**
     * Simple method for detecting outliers, provided for backward compatibility
     */
    public List<Integer> detectOutliers() {
        // Convert BigIntegers to BigDecimal
        List<BigDecimal> rates = sourceRates.stream()
            .map(BigDecimal::new)
            .collect(Collectors.toList());
        
        return detectOutliersMAD(rates);
    }
    
    /**
     * Generates a fair rate proof by combining rates, weights, and validation
     * for on-chain verification
     */
    public static byte[] generateFairRateProof(List<String> sources, List<BigInteger> rates, 
                                             List<BigInteger> volumes, List<Integer> trustScores,
                                             BigInteger finalRate) {
        // In a real implementation, this would create a cryptographic proof
        // that the calculations were performed correctly
        
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        
        // Write the number of sources
        buffer.putInt(sources.size());
        
        // Write each source and its data
        for (int i = 0; i < sources.size(); i++) {
            // Source name
            byte[] sourceBytes = sources.get(i).getBytes();
            buffer.putInt(sourceBytes.length);
            buffer.put(sourceBytes);
            
            // Rate
            byte[] rateBytes = rates.get(i).toByteArray();
            buffer.putInt(rateBytes.length);
            buffer.put(rateBytes);
            
            // Volume
            byte[] volumeBytes = volumes.get(i).toByteArray();
            buffer.putInt(volumeBytes.length);
            buffer.put(volumeBytes);
            
            // Trust score
            buffer.putInt(trustScores.get(i));
        }
        
        // Write the final calculated rate
        byte[] finalRateBytes = finalRate.toByteArray();
        buffer.putInt(finalRateBytes.length);
        buffer.put(finalRateBytes);
        
        // Add a timestamp
        buffer.putLong(System.currentTimeMillis());
        
        // Finalize
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        
        return result;
    }
    
    @Override
    public boolean isValid() {
        if (!super.isValid()) {
            return false;
        }
        
        // Additional validation specific to currency exchange transactions
        if (sourceTokenSymbol == null || sourceTokenSymbol.isEmpty() ||
            targetTokenSymbol == null || targetTokenSymbol.isEmpty()) {
            return false;
        }
        
        if (sourceAmount == null || sourceAmount.compareTo(BigInteger.ZERO) <= 0 ||
            targetAmount == null || targetAmount.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }
        
        if (exchangeRate == null || exchangeRate.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }
        
        if (userAddress == null || bankAddress == null) {
            return false;
        }
        
        if (exchangeSources == null || exchangeSources.isEmpty() || exchangeSources.size() < 3) {
            return false;
        }
        
        if (fairWeightingProof == null || fairWeightingProof.length == 0) {
            return false;
        }
        
        if (sourceRates == null || sourceRates.isEmpty() || 
            sourceVolumes == null || sourceVolumes.isEmpty() ||
            trustScores == null || trustScores.isEmpty()) {
            return false;
        }
        
        // Verify the fairness of the exchange rate
        return verifyFairRate();
    }
    
    @Override
    public String toString() {
        return "CurrencyExchangeTransaction{" +
                "sourceToken='" + sourceTokenSymbol + '\'' +
                ", targetToken='" + targetTokenSymbol + '\'' +
                ", sourceAmount=" + sourceAmount +
                ", targetAmount=" + targetAmount +
                ", exchangeRate=" + exchangeRate +
                ", exchangeId='" + exchangeId + '\'' +
                ", exchangeDate=" + exchangeDate +
                ", user=" + userAddress +
                ", bank=" + bankAddress +
                ", sources=" + exchangeSources.size() +
                '}';
    }
}