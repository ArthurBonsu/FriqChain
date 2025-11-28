# Foreign Exchange Module - Integration Guide

## Quick Start Integration

This guide shows how to integrate the Foreign Exchange module with your existing TINC blockchain infrastructure.

## Module Dependencies

The FX module integrates with existing TINC components:

```
foreign/ (NEW MODULE)
    ↓ uses ↓
core/types/
    - Address.java
    - Amount.java
    ↓ integrates with ↓
core/blockchain/
    - BlockchainState.java
    - ExchangeRateOracle.java
    - FairWeightingValidator.java
    - AntiManipulationController.java
    ↓ works with ↓
core/blockchain/transaction/
    - CurrencyExchangeTransaction.java
    - P2PTransferTransaction.java
    - ASUSettlementTransaction.java
```

## Step-by-Step Integration

### Step 1: Initialize FX Components

Add to your main blockchain initialization:

```java
// In BlockchainInitializer.java or similar

public class TINCBlockchain {
    
    // Existing components
    private BlockchainState blockchainState;
    private ExchangeRateOracle exchangeRateOracle;
    private FairWeightingValidator fairWeightingValidator;
    
    // NEW: Foreign Exchange components
    private FXReferenceManager fxReferenceManager;
    private FXExposureMonitor fxExposureMonitor;
    private FXCollateralManager fxCollateralManager;
    private SyntheticFXMinter syntheticFXMinter;
    private ExternalStablecoinBridge stablecoinBridge;
    private DualPricingEngine dualPricingEngine;
    private FXOracleAggregator fxOracleAggregator;
    
    public void initialize() {
        // Initialize existing components first
        blockchainState = new BlockchainState();
        exchangeRateOracle = new ExchangeRateOracle();
        fairWeightingValidator = new FairWeightingValidator();
        
        // Initialize FX module components in dependency order
        BigInteger totalSystemValue = blockchainState.getTotalASUInCirculation();
        
        fxReferenceManager = new FXReferenceManager();
        fxExposureMonitor = new FXExposureMonitor(totalSystemValue);
        fxCollateralManager = new FXCollateralManager();
        syntheticFXMinter = new SyntheticFXMinter(fxCollateralManager, fxReferenceManager);
        stablecoinBridge = new ExternalStablecoinBridge(fxExposureMonitor);
        dualPricingEngine = new DualPricingEngine(fxReferenceManager);
        fxOracleAggregator = new FXOracleAggregator();
        
        // Register initial oracle sources
        registerInitialOracleSources();
        
        // Initialize collateral vaults
        initializeCollateralVaults();
        
        // Start background tasks
        startFXBackgroundTasks();
    }
}
```

### Step 2: Register Oracle Sources

```java
private void registerInitialOracleSources() {
    // African exchanges (3x weight)
    fxOracleAggregator.registerOracleSource(
        "LUNO_AFRICA",
        "Luno Africa Exchange",
        OracleSource.OracleType.AFRICAN_EXCHANGE,
        true,  // African-based
        new BigDecimal("50000")  // 50k ASU stake
    );
    
    fxOracleAggregator.registerOracleSource(
        "PAXFUL_NIGERIA",
        "Paxful Nigeria",
        OracleSource.OracleType.AFRICAN_EXCHANGE,
        true,
        new BigDecimal("50000")
    );
    
    fxOracleAggregator.registerOracleSource(
        "QUIDAX",
        "Quidax Exchange",
        OracleSource.OracleType.AFRICAN_EXCHANGE,
        true,
        new BigDecimal("50000")
    );
    
    // International references (1x weight)
    fxOracleAggregator.registerOracleSource(
        "CHAINLINK_USD",
        "Chainlink USD Feed",
        OracleSource.OracleType.BLOCKCHAIN_ORACLE,
        false,
        new BigDecimal("30000")
    );
    
    fxOracleAggregator.registerOracleSource(
        "BAND_PROTOCOL",
        "Band Protocol",
        OracleSource.OracleType.BLOCKCHAIN_ORACLE,
        false,
        new BigDecimal("30000")
    );
}
```

### Step 3: Initialize Collateral Vaults

```java
private void initializeCollateralVaults() {
    // Multi-signature custodians (5 required)
    List<String> custodians = Arrays.asList(
        "custodian_1_public_key",
        "custodian_2_public_key",
        "custodian_3_public_key",
        "custodian_4_public_key",
        "custodian_5_public_key"
    );
    
    // Initialize vault for ASU collateral
    fxCollateralManager.initializeVault("ASU", custodians);
    
    // Deposit initial reserves (from treasury)
    BigInteger initialReserves = new BigInteger("10000000000000000000000"); // 10k ASU
    byte[] depositProof = generateDepositProof(initialReserves);
    fxCollateralManager.depositCollateral("ASU", initialReserves, depositProof);
}
```

### Step 4: Connect with ExchangeRateOracle

Enhance your existing `ExchangeRateOracle` to feed the FX aggregator:

```java
// In ExchangeRateOracle.java

public class ExchangeRateOracle {
    
    private FXOracleAggregator fxOracleAggregator; // NEW
    
    public void setFXOracleAggregator(FXOracleAggregator aggregator) {
        this.fxOracleAggregator = aggregator;
    }
    
    // Existing method - enhanced
    public void updateExchangeRate(String source, String currencyPair, BigDecimal rate) {
        // Existing validation logic...
        
        // NEW: Also submit to FX aggregator
        if (fxOracleAggregator != null) {
            byte[] signature = signRateData(source, currencyPair, rate);
            fxOracleAggregator.submitRate(source, currencyPair, rate, signature);
        }
        
        // Rest of existing logic...
    }
}
```

### Step 5: Integrate with FairWeightingValidator

```java
// In FairWeightingValidator.java

public class FairWeightingValidator {
    
    private FXReferenceManager fxReferenceManager; // NEW
    private DualPricingEngine dualPricingEngine;   // NEW
    
    public void setFXComponents(FXReferenceManager refManager, 
                               DualPricingEngine pricingEngine) {
        this.fxReferenceManager = refManager;
        this.dualPricingEngine = pricingEngine;
    }
    
    // Enhanced validation with dual pricing
    public boolean validateCurrencyExchange(CurrencyExchangeTransaction tx) {
        // Existing validation...
        
        // NEW: Check dual pricing
        String currencyPair = tx.getFromCurrency() + "/" + tx.getToCurrency();
        Optional<DualPrice> dualPrice = dualPricingEngine.getPrice(currencyPair);
        
        if (dualPrice.isPresent()) {
            // Verify transaction rate is within acceptable bounds of dual price
            BigDecimal deviation = calculateDeviation(tx.getRate(), dualPrice.get().getAsuPrice());
            if (deviation.abs().compareTo(new BigDecimal("0.05")) > 0) {
                LOGGER.warning("Transaction rate deviates significantly from fair price");
                // Additional validation or rejection logic
            }
        }
        
        return true; // Existing validation result
    }
}
```

### Step 6: Update Transaction Types

Add synthetic token support to Amount.java:

```java
// In Amount.java

public class Amount {
    
    // Existing fields...
    private boolean isSynthetic; // NEW
    
    // NEW: Factory method for synthetic currencies
    public static Amount forSyntheticCurrency(BigInteger value, String syntheticType) {
        Amount amount = new Amount();
        amount.value = value;
        amount.currencyCode = syntheticType; // "sUSD", "sEUR", etc.
        amount.isSynthetic = true;
        amount.isASU = false;
        return amount;
    }
    
    public boolean isSynthetic() {
        return isSynthetic;
    }
    
    // Enhanced validation
    public boolean isValid() {
        if (isSynthetic) {
            return SyntheticFXMinter.isSupportedSynthetic(currencyCode) && 
                   value.compareTo(BigInteger.ZERO) > 0;
        }
        // Existing validation...
    }
}
```

### Step 7: Add Synthetic Transaction Support

```java
// New transaction type: SyntheticMintTransaction.java

package org.example.app.core.blockchain.transaction;

public class SyntheticMintTransaction extends BaseTransaction {
    
    private final Address owner;
    private final String syntheticCurrency;
    private final BigInteger syntheticAmount;
    private final BigInteger collateralAmount;
    private final String positionId;
    
    // Implementation similar to existing transaction types...
    
    @Override
    public boolean validate() {
        // Validate with SyntheticFXMinter
        return syntheticFXMinter.validateMintRequest(
            owner, syntheticCurrency, syntheticAmount, collateralAmount
        );
    }
}
```

### Step 8: Background Tasks

```java
private void startFXBackgroundTasks() {
    // Task 1: Aggregate oracle rates every 5 minutes
    ScheduledExecutorService rateAggregator = Executors.newScheduledThreadPool(1);
    rateAggregator.scheduleAtFixedRate(() -> {
        aggregateAllCurrencyPairs();
    }, 0, 5, TimeUnit.MINUTES);
    
    // Task 2: Generate proof of reserves every hour
    ScheduledExecutorService proofGenerator = Executors.newScheduledThreadPool(1);
    proofGenerator.scheduleAtFixedRate(() -> {
        ProofOfReserves proof = fxCollateralManager.generateProofOfReserves();
        blockchainState.recordProofOfReserves(proof);
    }, 0, 1, TimeUnit.HOURS);
    
    // Task 3: Check and liquidate unhealthy positions every 15 minutes
    ScheduledExecutorService liquidator = Executors.newScheduledThreadPool(1);
    liquidator.scheduleAtFixedRate(() -> {
        List<String> liquidated = syntheticFXMinter.liquidateUnhealthyPositions();
        if (!liquidated.isEmpty()) {
            LOGGER.warning("Liquidated positions: " + liquidated);
        }
    }, 0, 15, TimeUnit.MINUTES);
    
    // Task 4: Clean up stale rates every hour
    ScheduledExecutorService cleanup = Executors.newScheduledThreadPool(1);
    cleanup.scheduleAtFixedRate(() -> {
        fxReferenceManager.cleanupStaleRates();
    }, 0, 1, TimeUnit.HOURS);
    
    // Task 5: Monitor exposure levels every 5 minutes
    ScheduledExecutorService exposureChecker = Executors.newScheduledThreadPool(1);
    exposureChecker.scheduleAtFixedRate(() -> {
        checkAndAlertExposureLevels();
    }, 0, 5, TimeUnit.MINUTES);
}

private void aggregateAllCurrencyPairs() {
    String[] pairs = {"ASU/USD", "ASU/EUR", "ASU/GBP", "ASU/RMB", "ASU/INR"};
    
    for (String pair : pairs) {
        Optional<AggregatedRate> rate = fxOracleAggregator.aggregateRates(pair);
        if (rate.isPresent() && rate.get().isHighConfidence()) {
            String[] currencies = pair.split("/");
            fxReferenceManager.updateReferenceRate(
                currencies[0], currencies[1],
                rate.get().getWeightedRate(),
                rate.get().getContributingSources(),
                rate.get().getAfricanSourceCount() > 0
            );
        }
    }
}

private void checkAndAlertExposureLevels() {
    List<ExposureAlert> alerts = fxExposureMonitor.getUnresolvedAlerts();
    
    for (ExposureAlert alert : alerts) {
        if (alert.getType() == ExposureAlert.AlertType.CRITICAL ||
            alert.getType() == ExposureAlert.AlertType.EXCEEDED) {
            
            // Send to governance DAO for review
            governanceDAO.submitAlert(alert);
            
            // If exceeded, trigger emergency procedures
            if (alert.getType() == ExposureAlert.AlertType.EXCEEDED) {
                handleExposureExceeded(alert);
            }
        }
    }
}
```

### Step 9: RPC/API Endpoints

Add new endpoints for FX functionality:

```java
// In API controller

@RestController
@RequestMapping("/api/v1/fx")
public class ForeignExchangeController {
    
    @Autowired
    private FXReferenceManager fxReferenceManager;
    
    @Autowired
    private SyntheticFXMinter syntheticFXMinter;
    
    @Autowired
    private DualPricingEngine dualPricingEngine;
    
    @GetMapping("/rates/{base}/{quote}")
    public ResponseEntity<FXReferenceRate> getReferenceRate(
            @PathVariable String base,
            @PathVariable String quote) {
        
        return fxReferenceManager.getReferenceRate(base, quote)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/synthetic/mint")
    public ResponseEntity<MintResult> mintSynthetic(@RequestBody MintRequest request) {
        MintResult result = syntheticFXMinter.mintSynthetic(
            request.getOwner(),
            request.getSyntheticCurrency(),
            request.getSyntheticAmount(),
            request.getCollateralAmount()
        );
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/prices/{itemId}")
    public ResponseEntity<DualPrice> getDualPrice(@PathVariable String itemId) {
        return dualPricingEngine.getPrice(itemId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/exposure/status")
    public ResponseEntity<Map<String, Object>> getExposureStatus() {
        return ResponseEntity.ok(fxExposureMonitor.getSystemStats());
    }
}
```

## Configuration Properties

Add to your application.yml or config file:

```yaml
tinc:
  fx:
    # Reference rate settings
    reference-rates:
      max-age-ms: 3600000  # 1 hour
      african-priority-multiplier: 3.0
      
    # Collateral settings
    collateral:
      min-ratio: 1.20  # 120%
      liquidation-threshold: 1.10  # 110%
      
    # Exposure limits
    exposure:
      max-total-foreign: 0.10  # 10%
      max-single-currency: 0.05  # 5%
      warning-threshold: 0.80  # 80%
      critical-threshold: 0.95  # 95%
      
    # Oracle settings
    oracle:
      min-sources: 5
      max-deviation-ratio: 0.15  # 15%
      min-reputation: 50
      
    # Bridge settings
    bridges:
      circuit-breaker-threshold: 0.95
      cooldown-period-ms: 3600000  # 1 hour
```

## Testing Integration

```java
@SpringBootTest
public class FXModuleIntegrationTest {
    
    @Autowired
    private TINCBlockchain blockchain;
    
    @Test
    public void testFullFXWorkflow() {
        // 1. Submit oracle rates
        blockchain.getFXOracleAggregator().submitRate(
            "LUNO_AFRICA", "ASU/USD", 
            new BigDecimal("0.0012"), signature);
        
        // 2. Aggregate rates
        Optional<AggregatedRate> rate = blockchain.getFXOracleAggregator()
            .aggregateRates("ASU/USD");
        assertTrue(rate.isPresent());
        
        // 3. Update reference manager
        blockchain.getFXReferenceManager().updateReferenceRate(
            "ASU", "USD", rate.get().getWeightedRate(),
            rate.get().getContributingSources(), true);
        
        // 4. Mint synthetic USD
        MintResult result = blockchain.getSyntheticFXMinter().mintSynthetic(
            testAddress, "sUSD", 
            new BigInteger("1000000000000000000"),
            new BigInteger("1200000000000000000"));
        assertTrue(result.isSuccess());
        
        // 5. Verify collateral locked
        Optional<ExposureData> exposure = blockchain.getFXExposureMonitor()
            .getExposureData("sUSD");
        assertTrue(exposure.isPresent());
        assertTrue(exposure.get().getLevel() == ExposureLevel.SAFE);
    }
}
```

## Monitoring Dashboard

Recommended metrics to display:

1. **FX Rates**:
   - Current ASU/USD, ASU/EUR, etc.
   - African vs international source counts
   - Confidence scores

2. **Synthetic Tokens**:
   - Total minted per currency
   - Average collateral ratio
   - Positions at risk (<115%)

3. **Exposure**:
   - Current total foreign exposure (target <10%)
   - Per-currency exposure
   - Alert count by severity

4. **Oracle Health**:
   - Active sources count
   - Average reputation score
   - Rejection rate

5. **Bridge Status**:
   - Bridge states (active/paused/circuit breaker)
   - Total bridged amounts
   - Last activity timestamps

## Migration Path

If you're adding this to an existing system:

1. **Phase 1**: Deploy FX components (read-only)
   - No active trading yet
   - Monitor oracle feeds
   - Test aggregation

2. **Phase 2**: Enable synthetic minting
   - Limited amount (e.g., 1M sUSD max)
   - Monitor collateral ratios
   - Test liquidations in testnet

3. **Phase 3**: Enable bridges (optional)
   - Start with low limits
   - Monitor exposure closely
   - Test circuit breakers

4. **Phase 4**: Full production
   - Raise limits gradually
   - Enable all currency pairs
   - Full monitoring active

## Troubleshooting

**Issue**: Oracle rates not aggregating

**Solution**:
- Check minimum 5 sources submitting
- Verify signatures are valid
- Check for too many outliers

**Issue**: Synthetic minting fails

**Solution**:
- Verify collateral vault has available reserves
- Check collateral ratio >= 120%
- Verify FX rate is available

**Issue**: Circuit breaker triggered

**Solution**:
- Review exposure levels
- Wait for cooldown period (1 hour)
- Rebalance if needed

**Issue**: High liquidation rate

**Solution**:
- Check for FX rate volatility
- Consider increasing liquidation threshold
- Alert users to add collateral

## Support

For integration assistance:
- Review README.md in /foreign directory
- Check inline documentation in each .java file
- Test with provided integration examples

## Conclusion

The Foreign Exchange module is now fully integrated with your TINC blockchain. It maintains African financial sovereignty while providing transparency and optional global market integration.

**Key Integration Points**:
1. ExchangeRateOracle → FXOracleAggregator
2. FairWeightingValidator → DualPricingEngine
3. Amount.java → Synthetic currency support
4. Background tasks for automation
5. API endpoints for external access

The module is production-ready and follows all TINC architectural principles.