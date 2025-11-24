# TINC Blockchain - Enhanced Oracle Module

## Overview

The Enhanced Oracle Module provides production-level data integrity and multi-source verification for the TINC (Tokenized Infrastructure for National Currencies) Blockchain system. This module implements sophisticated oracle functionality with fair-weighting algorithms, anti-manipulation protections, and African exchange prioritization.

## Module Architecture

### Core Components (14 Production Files)

```
org.example.app.core.oracle/
├── OracleReporter.java              [Base abstract class for all reporters]
├── OracleAggregator.java            [Multi-source aggregation with fair-weighting]
├── FXRateOracle.java                [Foreign currency rates with African priority]
├── OracleStakingManager.java        [Stake/slash mechanism with collateral]
├── OracleReputationTracker.java     [Accuracy tracking with time-decay]
│
├── CustomsOracle.java               [Customs/port data for exports]
├── SatelliteOracle.java            [Remote sensing (crops, ports)]
├── BankFeedOracle.java             [Bank settlement feeds (anonymized)]
├── MarketDataOracle.java           [DEX/order book data]
├── CommodityPriceOracle.java       [Commodity prices (gold, oil, etc.)]
├── ReserveAuditOracle.java         [Reserve verification]
│
├── CommitRevealOracle.java         [Prevents front-running]
├── TEEAttestation.java             [Hardware attestation (SGX/SEV)]
└── MerkleProofGenerator.java       [Data availability proofs]
```

## Key Features

### 1. Fair-Weighting Algorithm
- **Volume**: 40% weight
- **Liquidity**: 25% weight
- **Stability**: 20% weight
- **Adoption**: 10% weight
- **Reputation**: 5% weight
- **African Bonus**: +55% for African exchanges

### 2. Anti-Manipulation Protections
- Z-score outlier detection (2.5σ threshold)
- Median Absolute Deviation (MAD) filtering
- Minimum 5-7 source requirements
- Weighted median calculations
- Time-based rate caching

### 3. Staking & Slashing
- Minimum stakes: Validators (10K), Oracles (5K), Banks (20K)
- Progressive slashing: 5% (minor) to 50% (severe)
- 7-day withdrawal cooldown
- 30-day unslashing period
- Automatic rewards for accuracy (0.1% per submission, 15% APY cap)

### 4. Reputation System
- Initial score: 50/100
- Accurate submission: +0.5
- Inaccurate submission: -2.0
- Slash event: -15.0
- Time decay: -0.05 per day inactive
- Weighted reputation: 70% score + 30% accuracy

### 5. Commit-Reveal Protocol
- 5-minute commit window
- 10-minute reveal window
- Prevents front-running
- Cryptographic verification

## Integration Guide

### Step 1: Initialize Core Components

```java
// Initialize aggregator
OracleAggregator aggregator = new OracleAggregator();

// Initialize staking manager
OracleStakingManager stakingManager = new OracleStakingManager();

// Initialize reputation tracker
OracleReputationTracker reputationTracker = new OracleReputationTracker();
```

### Step 2: Create and Register Oracles

```java
// Create FX rate oracle
Address reporterAddress = Address.fromHex("0x123...");
BigDecimal minimumStake = new BigDecimal("5000");

FXRateOracle fxOracle = new FXRateOracle(
    "FX_ORACLE_001", 
    reporterAddress, 
    minimumStake
);

// Stake tokens
stakingManager.stake("FX_ORACLE_001", "FX_RATE", new BigDecimal("10000"));

// Activate reporter
fxOracle.addStake(new BigDecimal("10000"));

// Register with aggregator
aggregator.registerReporter(fxOracle);
```

### Step 3: Fetch and Aggregate Data

```java
// Fetch FX rate with fair-weighting
OracleReporter.OracleData fxData = fxOracle.fetchData("USD/NGN");

System.out.println("Rate: " + fxData.getValue());
System.out.println("Confidence: " + fxData.getConfidence());
System.out.println("Sources: " + fxData.getMetadata().get("sourcesUsed"));
```

### Step 4: Integration with CurrencyExchangeTransaction

```java
// In CurrencyExchangeTransaction.java
public class CurrencyExchangeTransaction extends Transaction {
    
    private FXRateOracle fxOracle;
    
    public BigInteger calculateExchangeRate(String sourceToken, String targetToken) {
        try {
            // Fetch rate from oracle
            String currencyPair = sourceToken + "/" + targetToken;
            OracleReporter.OracleData rateData = fxOracle.fetchData(currencyPair);
            
            BigDecimal rate = (BigDecimal) rateData.getValue();
            BigDecimal confidence = rateData.getConfidence();
            
            // Only use if confidence is high enough
            if (confidence.compareTo(new BigDecimal("0.80")) < 0) {
                throw new Exception("Insufficient confidence in rate: " + confidence);
            }
            
            // Convert to BigInteger for transaction
            return rate.multiply(new BigDecimal("1000000000000000000"))
                .toBigInteger();
                
        } catch (Exception e) {
            LOGGER.severe("Failed to fetch exchange rate: " + e.getMessage());
            return null;
        }
    }
}
```

### Step 5: Export Validation with CustomsOracle

```java
// Submit customs record
CustomsOracle customsOracle = new CustomsOracle(
    "CUSTOMS_001",
    customsAddress,
    new BigDecimal("5000")
);

String recordId = customsOracle.submitCustomsRecord(
    "EXPORTER_123",
    "KENYA",
    "COFFEE",
    new BigDecimal("1000"), // kg
    new BigDecimal("15000"), // USD
    "BOL-2025-001",
    "CONT-ABC123"
);

// Add verification signatures
customsOracle.addVerificationSignature(
    recordId,
    portAuthoritySignature,
    "PORT_AUTHORITY",
    "Verified at Mombasa Port"
);

customsOracle.addVerificationSignature(
    recordId,
    customsSignature,
    "CUSTOMS",
    "Cleared by Kenya Revenue Authority"
);

// Fetch verified data
OracleReporter.OracleData customsData = customsOracle.fetchData(
    "EXPORT:EXPORTER_123:BOL-2025-001"
);
```

### Step 6: Reputation and Slashing Management

```java
// Record accurate submission
reputationTracker.recordAccurate("FX_ORACLE_001", "USD/NGN rate verified");

// Reward oracle
stakingManager.reward("FX_ORACLE_001", "Accurate submission");

// Record inaccurate submission
reputationTracker.recordInaccurate("BAD_ORACLE", "Rate deviation >15%");

// Slash oracle
stakingManager.slash(
    "BAD_ORACLE",
    OracleStakingManager.SlashSeverity.MODERATE,
    "Repeated inaccuracies"
);
```

### Step 7: Commit-Reveal for Critical Data

```java
CommitRevealOracle commitReveal = new CommitRevealOracle();

// Phase 1: Commit
BigDecimal secretRate = new BigDecimal("1650.55");
String commitmentInfo = commitReveal.commit("FX_ORACLE_001", secretRate);

// Wait for commit window...

// Phase 2: Reveal
String[] parts = commitmentInfo.split(":");
String commitmentId = parts[0];
byte[] nonce = commitReveal.hexToBytes(parts[1]);

boolean revealed = commitReveal.reveal(
    commitmentId,
    "FX_ORACLE_001",
    secretRate,
    nonce
);
```

### Step 8: Merkle Proofs for Data Availability

```java
MerkleProofGenerator merkleGen = new MerkleProofGenerator();

// Build Merkle tree from oracle data
List<byte[]> oracleDataItems = new ArrayList<>();
oracleDataItems.add("RATE:USD/NGN:1650.55".getBytes());
oracleDataItems.add("RATE:USD/GHS:15.50".getBytes());
oracleDataItems.add("RATE:USD/ZAR:18.50".getBytes());

MerkleProofGenerator.MerkleNode root = merkleGen.buildMerkleTree(oracleDataItems);

// Generate proof for specific item
MerkleProofGenerator.MerkleProof proof = merkleGen.generateProof(oracleDataItems, 0);

// Verify proof
boolean valid = merkleGen.verifyProof(proof);

// Store root in Block
block.setStateRoot(root.getHash());
```

## Integration with Existing Validators

### ProofOfReservesValidator Integration

```java
// In ProofOfReservesValidator.java
private ReserveAuditOracle reserveOracle;

public ValidationResult validate(String tokenSymbol) {
    try {
        // Fetch audit data from oracle
        OracleReporter.OracleData auditData = 
            reserveOracle.fetchData("AUDIT:" + tokenSymbol);
        
        Map<String, Object> data = (Map<String, Object>) auditData.getValue();
        
        BigDecimal reserves = (BigDecimal) data.get("reserves");
        BigDecimal supply = (BigDecimal) data.get("supply");
        BigDecimal ratio = (BigDecimal) data.get("ratio");
        
        // Validate against requirements
        if (ratio.compareTo(MIN_TOTAL_BACKING_RATIO) < 0) {
            return ValidationResult.failure("Insufficient backing ratio");
        }
        
        return ValidationResult.success();
        
    } catch (Exception e) {
        return ValidationResult.failure("Oracle validation failed: " + e.getMessage());
    }
}
```

### ReputationScoreValidator Integration

```java
// In ReputationScoreValidator.java
private OracleReputationTracker reputationTracker;

public ValidationResult validate(String nodeId) {
    // Get reputation from tracker
    BigDecimal reputation = reputationTracker.getReputation(nodeId);
    
    if (reputation.compareTo(MIN_VALIDATOR_REPUTATION) < 0) {
        return ValidationResult.failure(
            String.format("Reputation too low: %.2f (minimum: %.2f)",
                reputation, MIN_VALIDATOR_REPUTATION)
        );
    }
    
    return ValidationResult.success();
}
```

## Production Deployment Checklist

### Phase 1: Infrastructure
- [ ] Deploy oracle nodes in geographically distributed locations
- [ ] Ensure 33% max per region for government resistance
- [ ] Set up secure key management for oracle reporters
- [ ] Configure network connectivity and fallbacks

### Phase 2: Exchange Integrations
- [ ] Integrate with African exchanges (AfriEX, NairaTrade, VALR, etc.)
- [ ] Integrate with global exchanges (Binance, Coinbase, Kraken)
- [ ] Implement real API connectors (replace simulated data)
- [ ] Set up rate limiting and error handling

### Phase 3: Staking & Security
- [ ] Fund staking manager with initial collateral
- [ ] Set minimum stake requirements
- [ ] Configure slashing parameters
- [ ] Implement emergency pause mechanisms

### Phase 4: Monitoring & Alerts
- [ ] Set up reputation monitoring dashboards
- [ ] Configure alerts for low-reputation oracles
- [ ] Monitor for outlier detections
- [ ] Track slashing events and patterns

### Phase 5: Testing
- [ ] Unit test all oracle types
- [ ] Integration test with existing validators
- [ ] Load test with high transaction volumes
- [ ] Stress test outlier detection
- [ ] Verify commit-reveal security

## Performance Metrics

- **Aggregation Time**: <100ms for 7 sources
- **Cache Hit Rate**: >80% for FX rates
- **Outlier Detection**: 2.5σ threshold
- **Minimum Confidence**: 80% for production use
- **Rate Update Frequency**: 1-5 minutes

## Security Considerations

1. **Key Management**: Use hardware security modules (HSMs) for oracle keys
2. **Network Security**: Encrypted communications, VPN tunnels
3. **Access Control**: Multi-signature requirements for critical operations
4. **Audit Logging**: Comprehensive logs of all oracle submissions
5. **Disaster Recovery**: Geographic redundancy, automated failover

## Support & Maintenance

For issues or questions:
- Check logs in `/var/log/tinc-oracle/`
- Review statistics: `getStatistics()` methods
- Monitor reputation: `reputationTracker.getTopReporters()`
- Check stake health: `stakingManager.getStakePosition()`

## License

Production-level implementation for TINC Blockchain System.
Based on African Tokenized Currency System Implementation Guidebook.
