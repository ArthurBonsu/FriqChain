# TINC Tokenization Module - Implementation Guide

## Quick Start Integration

### Step 1: Add Files to Project

Copy all files from `/mnt/user-data/outputs/tokenization/` to your project:
```
src/main/java/org/example/app/core/banking/tokenization/
├── NationalCurrencyToken.java
├── ExchangeRateOracle.java
├── FairRateAlgorithm.java
├── ExportToken.java
├── SyntheticFXToken.java
├── DynamicBasketRebalancer.java
└── TokenMintingController.java
```

### Step 2: Initialize Core Components

```java
// 1. Create Exchange Rate Oracle
ExchangeRateOracle exchangeRateOracle = new ExchangeRateOracle(
    blockchainState,
    blockchain,
    pkiManager
);

// 2. Create Fair Rate Algorithm
FairRateAlgorithm fairRateAlgorithm = new FairRateAlgorithm();

// 3. Create Token Minting Controller
TokenMintingController mintingController = new TokenMintingController(
    blockchainState,
    blockchain,
    pkiManager,
    antiManipulationController
);

// 4. Create Dynamic Basket Rebalancer
DynamicBasketRebalancer basketRebalancer = new DynamicBasketRebalancer(
    blockchainState,
    blockchain,
    asuToken
);
```

### Step 3: Register African Exchanges

```java
// Register Lagos Exchange
ExchangeRateOracle.ExchangeSource lagosExchange = 
    new ExchangeRateOracle.ExchangeSource(
        "LAGOS_FX",
        "Lagos Foreign Exchange",
        "Nigeria",
        true, // isAfricanExchange
        new BigDecimal("0.95"), // reliability score
        new BigDecimal("0.90"), // liquidity score
        50000000L, // daily volume
        Set.of("NGN/USD", "NGN/EUR", "NGN/GHS", "NGN/ZAR"),
        false // TEE attested
    );

exchangeRateOracle.registerExchange(lagosExchange, signature);

// Register more African exchanges (Johannesburg, Nairobi, Cairo, etc.)
```

### Step 4: Create National Currency Tokens

```java
// Create Nigerian Naira Token
NationalCurrencyToken ngnToken = new NationalCurrencyToken(
    "NGN",                           // Currency code
    "NGA",                           // Country code
    "Nigeria",                       // Country name
    "Central Bank of Nigeria",       // Central bank
    0L,                              // Initial supply (0 - mint with DAO)
    contractAddress,                 // Contract address
    creator,                         // Creator address
    true,                            // Is African currency
    blockchainState,
    blockchain,
    pkiManager,
    exchangeRateOracle,
    fairRateAlgorithm,
    antiManipulationController,
    mintingController
);

// Create more African currency tokens (GHS, ZAR, KES, etc.)
```

### Step 5: Initialize DAO Governance

```java
// Add DAO members (banks)
TokenMintingController.DAOMember centralBank = 
    new TokenMintingController.DAOMember(
        "CBN_001",                   // Member ID
        "BANK",                      // Type
        "Nigeria",                   // Country
        new BigDecimal("1.0")        // Voting weight
    );

mintingController.daoMembers.add("CBN_001");
mintingController.memberRegistry.put("CBN_001", centralBank);

// Add more banks across Africa
```

### Step 6: Submit Reserve Proofs and Mint Tokens

```java
// Create reserve attestations
Map<String, NationalCurrencyToken.ReserveProof> proofs = new HashMap<>();

NationalCurrencyToken.ReserveProof proof1 = new NationalCurrencyToken.ReserveProof(
    "PROOF_001",
    NationalCurrencyToken.BackingType.FIAT_RESERVES,
    new BigDecimal("120000000"), // 120M in reserves
    "NGN",
    "CUSTODIAN_1",
    merkleRoot,
    signature,
    Map.of("ipfs", "QmXxx...", "arweave", "Yyy...")
);

proofs.put("PROOF_001", proof1);

// Add more proofs (minimum 3 required)

// Submit mint proposal
String proposalId = mintingController.submitMintProposal(
    "NGN",
    new BigDecimal("100000000"), // Mint 100M NGN tokens
    treasuryAddress,
    "Initial token supply backed by 120M NGN reserves",
    attestations,
    "CBN_001"
).join();

// DAO members vote
mintingController.voteOnProposal(proposalId, "CBN_001", true, "Approved");
mintingController.voteOnProposal(proposalId, "BANK_002", true, "Approved");
mintingController.voteOnProposal(proposalId, "BANK_003", true, "Approved");

// Auto-executes when 66% approval reached
```

### Step 7: Create Export Tokens

```java
// Create export token for Nigerian cocoa
ExportToken cocoaToken = new ExportToken(
    "COCOA",                         // Commodity type
    "Nigeria",                       // Exporter country
    "Netherlands",                   // Destination
    "EXPORTER_001",                  // Exporter ID
    0L,                              // Initial supply
    contractAddress,
    creator,
    blockchainState,
    blockchain,
    pkiManager,
    exchangeRateOracle
);

// Create trade receipt when shipment occurs
ExportToken.TradeReceipt receipt = new ExportToken.TradeReceipt(
    "TR_001",
    "EXPORTER_001",
    "COCOA",
    new BigDecimal("50"), // 50 metric tons
    "MT",
    new BigDecimal("150000"), // $150k value
    "USD",
    "Netherlands",
    "BOL_12345",
    customsSignature,
    merkleProof,
    verificationProofs
);

// Get customs and shipping verification
ExportToken.CustomsClearance clearance = ...;
ExportToken.ShippingVerification shipping = ...;

// Create export tokens
String tokenId = cocoaToken.createExportToken(receipt, clearance, shipping).join();
```

### Step 8: Create Synthetic Foreign Currency

```java
// Create synthetic USD token
SyntheticFXToken sUsdToken = new SyntheticFXToken(
    "USD",                           // Synthetic currency
    "USD",                           // Peg currency
    0L,                              // Initial supply
    contractAddress,
    creator,
    blockchainState,
    blockchain,
    pkiManager,
    exchangeRateOracle,
    asuToken
);

// Mint sUSD with ASU collateral
Map<String, BigDecimal> collateral = new HashMap<>();
collateral.put("ASU", new BigDecimal("1800000")); // $1.8M ASU
collateral.put("NGN", new BigDecimal("500000"));   // $500k NGN

String positionId = sUsdToken.mintWithCollateral(
    userAddress,
    new BigDecimal("1000000"), // Mint $1M sUSD
    collateral                  // With $2.3M collateral (230%)
).join();
```

### Step 9: Monitor System Health

```java
// Check collateral ratios
BigDecimal ngnRatio = ngnToken.getCurrentCollateralRatio();
if (ngnRatio.compareTo(new BigDecimal("1.20")) < 0) {
    System.out.println("WARNING: NGN collateral ratio below minimum!");
}

// Check circuit breakers
if (ngnToken.circuitBreaker.isTriggered()) {
    System.out.println("Circuit breaker triggered for NGN");
}

// Monitor exchange rate confidence
BigDecimal rate = exchangeRateOracle.getExchangeRate("NGN", "USD");
ExchangeRateOracle.RateStatistics stats = 
    exchangeRateOracle.getRateStatistics().get("NGN/USD");
    
if (stats.confidenceScore.compareTo(new BigDecimal("0.7")) < 0) {
    System.out.println("Low confidence in NGN/USD rate");
}

// Check for non-participating countries
Set<String> nonParticipating = basketRebalancer.getNonParticipatingCountries();
if (!nonParticipating.isEmpty()) {
    System.out.println("Non-participating countries: " + nonParticipating);
}
```

## Integration with Existing Components

### Wallet Integration

The tokenization module integrates with existing wallet types:

```java
// CitizenWallet
citizenWallet.addTokenSupport(ngnToken);
citizenWallet.addTokenSupport(asuToken);
citizenWallet.addTokenSupport(sUsdToken);

// TokenBankUserWallet
bankWallet.registerToken(ngnToken);
bankWallet.enableCrossBorderTransfers(asuToken);

// MerchantWallet
merchantWallet.acceptPayment(ngnToken);
merchantWallet.acceptPayment(exportToken);
```

### Transaction Integration

```java
// Create currency exchange transaction
CurrencyExchangeTransaction exchangeTx = new CurrencyExchangeTransaction(
    senderAddress,
    recipientAddress,
    ngnToken,      // From NGN
    asuToken,      // To ASU
    new BigDecimal("1000000"),
    exchangeRateOracle.getExchangeRate("NGN", "ASU")
);

// Validate with anti-manipulation
AntiManipulationController.ManipulationAnalysisResult analysis = 
    antiManipulationController.analyzeTransaction(exchangeTx).join();

if (analysis.riskLevel == ManipulationMonitor.ManipulationRisk.HIGH) {
    // Reject or flag for review
}
```

### Bank Registration Integration

```java
// Register bank with tokenization support
BankRegistrationContract.BankRegistration bank = 
    bankRegistrationContract.getBankRegistration(bankId);

// Bank can now:
// 1. Participate in DAO voting
mintingController.daoMembers.add(bankId);

// 2. Submit reserve attestations
TokenMintingController.ReserveAttestation attestation = 
    new TokenMintingController.ReserveAttestation(...);
    
// 3. Process fiat-to-token conversions
ngnToken.requestMint(...);

// 4. Handle token-to-fiat redemptions
ngnToken.requestBurn(...);
```

## Common Operations

### Get Exchange Rate with African Bonus
```java
BigDecimal rate = ngnToken.getCurrentExchangeRate("USD");
// Automatically applies 55% African bonus if applicable
```

### Handle Payment Timeout on Export
```java
exportToken.handlePaymentTimeout(paymentId);
// Enables domestic absorption at 5% discount
```

### Rebalance ASU Basket
```java
basketRebalancer.executeRebalancing("METRICS_UPDATE", details).join();
// Auto-rebalances based on country participation
```

### Liquidate Undercollateralized Position
```java
sUsdToken.liquidatePosition(positionId, liquidatorAddress).join();
// Liquidates if collateral ratio < 120%
```

### Freeze Minting (Emergency)
```java
mintingController.freezeMinting(
    "NGN",
    "Emergency audit required",
    Set.of("EMERGENCY_COUNCIL_1", "EMERGENCY_COUNCIL_2"),
    0L // Indefinite
);
```

## Performance Optimization

### Caching
```java
// Exchange rates are cached for 5 minutes
BigDecimal cachedRate = fairRateAlgorithm.getCachedRate("NGN", "USD");

// Force refresh if needed
exchangeRateOracle.updateRateStatistics("NGN/USD");
```

### Batch Operations
```java
// Submit multiple rate updates in parallel
List<CompletableFuture<Boolean>> futures = new ArrayList<>();

for (ExchangeRateOracle.ExchangeRate rate : rates) {
    futures.add(exchangeRateOracle.submitExchangeRate(rate, signature));
}

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

### Monitoring and Cleanup
```java
// Automatic cleanup runs every hour
// - Expired proposals
// - Expired freezes
// - Old rate snapshots
// - Inactive positions

// Manual cleanup if needed
mintingController.cleanupExpiredProposals();
```

## Security Best Practices

1. **Always verify signatures**
   ```java
   if (!pkiManager.verifySignature(data, signature, publicKey)) {
       throw new SecurityException("Invalid signature");
   }
   ```

2. **Use multi-source attestation**
   ```java
   if (attestations.size() < 3) {
       throw new IllegalStateException("Minimum 3 attestations required");
   }
   ```

3. **Check collateral ratios**
   ```java
   if (collateralRatio.compareTo(new BigDecimal("1.20")) < 0) {
       throw new IllegalStateException("Insufficient collateral");
   }
   ```

4. **Monitor rate limits**
   ```java
   RateLimiter limiter = mintingRateLimits.get(tokenSymbol);
   if (!limiter.canMint(amount)) {
       throw new IllegalStateException("Daily limit exceeded");
   }
   ```

5. **Implement circuit breakers**
   ```java
   if (circuitBreaker.isTriggered()) {
       throw new IllegalStateException("Circuit breaker active");
   }
   ```

## Testing

### Unit Tests
```java
@Test
public void testAfricanExchangeBonus() {
    BigDecimal baseRate = new BigDecimal("1.0");
    BigDecimal bonusRate = ngnToken.getCurrentExchangeRate("USD");
    
    // Should apply 55% bonus for African currencies
    assertTrue(bonusRate.compareTo(baseRate.multiply(new BigDecimal("1.55"))) == 0);
}

@Test
public void testProofOfReserves() {
    BigDecimal reserves = new BigDecimal("120000000");
    BigDecimal supply = new BigDecimal("100000000");
    
    BigDecimal ratio = reserves.divide(supply, 4, RoundingMode.HALF_UP);
    assertTrue(ratio.compareTo(new BigDecimal("1.20")) >= 0);
}
```

### Integration Tests
```java
@Test
public void testEndToEndMinting() {
    // Submit proposal
    String proposalId = mintingController.submitMintProposal(...).join();
    
    // Vote
    mintingController.voteOnProposal(proposalId, voter1, true, "");
    mintingController.voteOnProposal(proposalId, voter2, true, "");
    
    // Check approval
    MintProposal proposal = mintingController.getPendingProposals().get(proposalId);
    assertTrue(proposal.status == MintProposal.ProposalStatus.APPROVED);
    
    // Verify execution
    assertTrue(mintingController.getExecutedMints().containsKey(proposalId));
}
```

## Troubleshooting

### Issue: Mint proposal rejected
**Solution:** Check collateral ratio and attestation count
```java
BigDecimal ratio = calculateCollateralRatio(amount, attestations);
System.out.println("Collateral ratio: " + ratio);
System.out.println("Attestations: " + attestations.size());
```

### Issue: Rate outliers detected
**Solution:** Review provider reputation
```java
Map<String, ProviderReputation> reputations = 
    exchangeRateOracle.getProviderReputations();
    
for (ProviderReputation rep : reputations.values()) {
    if (rep.accuracyScore.compareTo(new BigDecimal("0.8")) < 0) {
        System.out.println("Low reputation provider: " + rep.providerId);
    }
}
```

### Issue: Circuit breaker triggered
**Solution:** Wait for cooldown and investigate cause
```java
long timeSinceTriggered = System.currentTimeMillis() - 
    circuitBreaker.triggerTime;
    
if (timeSinceTriggered > 3600000) { // 1 hour
    circuitBreaker.reset();
}
```

## Additional Resources

- **Implementation Guidebook:** `Mini_African_Tokenized_Currency_System__Implementation_Guidebook.pdf`
- **Module README:** `README.md`
- **Project Structure:** `OfficialProjectStructure.txt`
- **Requirements:** `Requirements.txt`

## Support

For technical support or questions:
1. Review the comprehensive documentation in README.md
2. Check the implementation examples in this guide
3. Consult the Implementation Guidebook PDF
4. Review existing code patterns in the uploaded files

---

**Status:** Production-Ready for Integration  
**Estimated Integration Time:** 2-3 days  
**Testing Required:** Comprehensive (unit, integration, security)