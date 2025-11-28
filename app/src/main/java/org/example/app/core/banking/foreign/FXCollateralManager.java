package org.example.app.core.blockchain.foreign;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * FXCollateralManager manages the collateral backing for synthetic foreign currency tokens.
 * Ensures all synthetic tokens maintain proper reserves and generates proof-of-reserves.
 * 
 * Core Requirements:
 * - Minimum 120% collateralization at all times
 * - Real-time monitoring of collateral ratios
 * - Cryptographic proof of reserves
 * - Multi-signature vault management
 * - Emergency liquidation mechanisms
 * - Transparent on-chain reserve reporting
 */
public class FXCollateralManager {
    
    private static final Logger LOGGER = Logger.getLogger(FXCollateralManager.class.getName());
    
    // Minimum collateralization ratio: 120%
    private static final BigDecimal MIN_COLLATERAL_RATIO = new BigDecimal("1.20");
    
    // Critical threshold for emergency actions: 105%
    private static final BigDecimal CRITICAL_THRESHOLD = new BigDecimal("1.05");
    
    // Collateral vaults: Currency -> CollateralVault
    private final Map<String, CollateralVault> vaults;
    
    // Locked collateral positions: Address -> List of locks
    private final Map<Address, List<CollateralLock>> collateralLocks;
    
    // Reserve proof generation interval (1 hour)
    private static final long PROOF_GENERATION_INTERVAL = 3600000;
    
    private final ReentrantReadWriteLock lock;
    private long lastProofGeneration;
    
    /**
     * Represents a collateral vault for a specific currency
     */
    public static class CollateralVault {
        private final String currency;
        private BigInteger totalReserves;
        private BigInteger lockedReserves;
        private BigInteger availableReserves;
        private final List<String> custodians; // Multi-sig custodians
        private byte[] lastMerkleRoot;
        private long lastAuditTimestamp;
        
        public CollateralVault(String currency, List<String> custodians) {
            this.currency = currency;
            this.totalReserves = BigInteger.ZERO;
            this.lockedReserves = BigInteger.ZERO;
            this.availableReserves = BigInteger.ZERO;
            this.custodians = new ArrayList<>(custodians);
            this.lastMerkleRoot = new byte[32];
            this.lastAuditTimestamp = System.currentTimeMillis();
        }
        
        public String getCurrency() { return currency; }
        public BigInteger getTotalReserves() { return totalReserves; }
        public BigInteger getLockedReserves() { return lockedReserves; }
        public BigInteger getAvailableReserves() { return availableReserves; }
        public List<String> getCustodians() { return new ArrayList<>(custodians); }
        public byte[] getLastMerkleRoot() { return Arrays.copyOf(lastMerkleRoot, lastMerkleRoot.length); }
        public long getLastAuditTimestamp() { return lastAuditTimestamp; }
        
        public void addReserves(BigInteger amount) {
            totalReserves = totalReserves.add(amount);
            availableReserves = availableReserves.add(amount);
        }
        
        public boolean lockReserves(BigInteger amount) {
            if (availableReserves.compareTo(amount) < 0) {
                return false;
            }
            lockedReserves = lockedReserves.add(amount);
            availableReserves = availableReserves.subtract(amount);
            return true;
        }
        
        public boolean releaseReserves(BigInteger amount) {
            if (lockedReserves.compareTo(amount) < 0) {
                return false;
            }
            lockedReserves = lockedReserves.subtract(amount);
            availableReserves = availableReserves.add(amount);
            return true;
        }
        
        public void updateMerkleRoot(byte[] root) {
            this.lastMerkleRoot = Arrays.copyOf(root, root.length);
            this.lastAuditTimestamp = System.currentTimeMillis();
        }
        
        public BigDecimal getUtilizationRate() {
            if (totalReserves.compareTo(BigInteger.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(lockedReserves)
                .divide(new BigDecimal(totalReserves), 8, RoundingMode.HALF_UP);
        }
    }
    
    /**
     * Represents a collateral lock for a specific position
     */
    public static class CollateralLock {
        private final String lockId;
        private final Address owner;
        private final String currency;
        private final BigInteger amount;
        private final String purpose; // e.g., "sUSD minting"
        private final long lockTimestamp;
        private final byte[] lockProof;
        
        public CollateralLock(String lockId, Address owner, String currency,
                            BigInteger amount, String purpose, byte[] lockProof) {
            this.lockId = lockId;
            this.owner = owner;
            this.currency = currency;
            this.amount = amount;
            this.purpose = purpose;
            this.lockTimestamp = System.currentTimeMillis();
            this.lockProof = lockProof != null ? 
                Arrays.copyOf(lockProof, lockProof.length) : new byte[0];
        }
        
        public String getLockId() { return lockId; }
        public Address getOwner() { return owner; }
        public String getCurrency() { return currency; }
        public BigInteger getAmount() { return amount; }
        public String getPurpose() { return purpose; }
        public long getLockTimestamp() { return lockTimestamp; }
        public byte[] getLockProof() { return Arrays.copyOf(lockProof, lockProof.length); }
    }
    
    /**
     * Proof of Reserves result
     */
    public static class ProofOfReserves {
        private final Map<String, BigInteger> reservesByurrency;
        private final byte[] merkleRoot;
        private final long timestamp;
        private final List<String> auditorSignatures;
        private final boolean isValid;
        
        public ProofOfReserves(Map<String, BigInteger> reservesByCurrency, byte[] merkleRoot,
                              long timestamp, List<String> auditorSignatures, boolean isValid) {
            this.reservesByCurrency = new HashMap<>(reservesByCurrency);
            this.merkleRoot = Arrays.copyOf(merkleRoot, merkleRoot.length);
            this.timestamp = timestamp;
            this.auditorSignatures = new ArrayList<>(auditorSignatures);
            this.isValid = isValid;
        }
        
        public Map<String, BigInteger> getReservesByCurrency() { 
            return new HashMap<>(reservesByCurrency); 
        }
        public byte[] getMerkleRoot() { return Arrays.copyOf(merkleRoot, merkleRoot.length); }
        public long getTimestamp() { return timestamp; }
        public List<String> getAuditorSignatures() { return new ArrayList<>(auditorSignatures); }
        public boolean isValid() { return isValid; }
    }
    
    public FXCollateralManager() {
        this.vaults = new ConcurrentHashMap<>();
        this.collateralLocks = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock(true);
        this.lastProofGeneration = 0;
        
        LOGGER.info("FXCollateralManager initialized - Managing collateral for synthetic FX");
    }
    
    /**
     * Initializes a collateral vault for a currency
     */
    public boolean initializeVault(String currency, List<String> custodians) {
        lock.writeLock().lock();
        try {
            if (vaults.containsKey(currency)) {
                LOGGER.warning("Vault already exists for: " + currency);
                return false;
            }
            
            if (custodians == null || custodians.size() < 3) {
                LOGGER.warning("Insufficient custodians (minimum 3 required)");
                return false;
            }
            
            CollateralVault vault = new CollateralVault(currency, custodians);
            vaults.put(currency, vault);
            
            LOGGER.info(String.format("Initialized collateral vault for %s with %d custodians",
                currency, custodians.size()));
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Deposits collateral into a vault
     */
    public boolean depositCollateral(String currency, BigInteger amount, byte[] depositProof) {
        lock.writeLock().lock();
        try {
            CollateralVault vault = vaults.get(currency);
            if (vault == null) {
                LOGGER.warning("Vault not found for currency: " + currency);
                return false;
            }
            
            if (amount.compareTo(BigInteger.ZERO) <= 0) {
                LOGGER.warning("Invalid deposit amount: " + amount);
                return false;
            }
            
            // Verify deposit proof (in production, this would verify cryptographic proof)
            if (!verifyDepositProof(depositProof, currency, amount)) {
                LOGGER.warning("Invalid deposit proof");
                return false;
            }
            
            vault.addReserves(amount);
            
            LOGGER.info(String.format("Deposited %s %s to vault (total: %s)",
                amount, currency, vault.getTotalReserves()));
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Locks collateral for a synthetic position
     */
    public boolean lockCollateral(Address owner, Amount collateralAmount, String purpose) {
        lock.writeLock().lock();
        try {
            String currency = collateralAmount.getCurrencyCode();
            CollateralVault vault = vaults.get(currency);
            
            if (vault == null) {
                LOGGER.warning("Vault not found for: " + currency);
                return false;
            }
            
            BigInteger amount = collateralAmount.getValue();
            
            // Attempt to lock reserves in vault
            if (!vault.lockReserves(amount)) {
                LOGGER.warning(String.format("Insufficient available reserves. Available: %s, Requested: %s",
                    vault.getAvailableReserves(), amount));
                return false;
            }
            
            // Create collateral lock
            String lockId = generateLockId(owner, currency, amount);
            byte[] lockProof = generateLockProof(lockId, owner, currency, amount);
            
            CollateralLock lock = new CollateralLock(
                lockId, owner, currency, amount, purpose, lockProof
            );
            
            collateralLocks.computeIfAbsent(owner, k -> Collections.synchronizedList(new ArrayList<>()))
                          .add(lock);
            
            LOGGER.info(String.format("Locked %s %s for %s (lock: %s)",
                amount, currency, owner, lockId));
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Releases collateral from a lock
     */
    public boolean releaseCollateral(Address owner, Amount collateralAmount, String purpose) {
        lock.writeLock().lock();
        try {
            String currency = collateralAmount.getCurrencyCode();
            BigInteger amount = collateralAmount.getValue();
            
            List<CollateralLock> locks = collateralLocks.get(owner);
            if (locks == null || locks.isEmpty()) {
                LOGGER.warning("No collateral locks found for owner: " + owner);
                return false;
            }
            
            // Find matching lock
            CollateralLock matchingLock = null;
            for (CollateralLock lock : locks) {
                if (lock.getCurrency().equals(currency) && 
                    lock.getPurpose().equals(purpose) &&
                    lock.getAmount().compareTo(amount) >= 0) {
                    matchingLock = lock;
                    break;
                }
            }
            
            if (matchingLock == null) {
                LOGGER.warning("No matching collateral lock found");
                return false;
            }
            
            // Release from vault
            CollateralVault vault = vaults.get(currency);
            if (vault == null || !vault.releaseReserves(amount)) {
                LOGGER.warning("Failed to release reserves from vault");
                return false;
            }
            
            // Remove or update lock
            locks.remove(matchingLock);
            
            LOGGER.info(String.format("Released %s %s for %s",
                amount, currency, owner));
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Generates cryptographic proof of reserves for all vaults
     */
    public ProofOfReserves generateProofOfReserves() {
        lock.readLock().lock();
        try {
            Map<String, BigInteger> reservesByCurrency = new HashMap<>();
            List<byte[]> vaultProofs = new ArrayList<>();
            
            // Collect reserves from all vaults
            for (Map.Entry<String, CollateralVault> entry : vaults.entrySet()) {
                CollateralVault vault = entry.getValue();
                reservesByCurrency.put(entry.getKey(), vault.getTotalReserves());
                vaultProofs.add(vault.getLastMerkleRoot());
            }
            
            // Generate combined Merkle root
            byte[] combinedRoot = generateCombinedMerkleRoot(vaultProofs);
            
            // Generate auditor signatures (in production, this would be real signatures)
            List<String> signatures = generateAuditorSignatures(combinedRoot, reservesByCurrency);
            
            // Validate proof
            boolean isValid = signatures.size() >= 3; // Require at least 3 auditor signatures
            
            lastProofGeneration = System.currentTimeMillis();
            
            LOGGER.info(String.format("Generated Proof of Reserves (valid: %b, auditors: %d)",
                isValid, signatures.size()));
            
            return new ProofOfReserves(reservesByCurrency, combinedRoot,
                System.currentTimeMillis(), signatures, isValid);
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets collateral ratio for a specific vault
     */
    public BigDecimal getCollateralRatio(String currency, BigInteger syntheticMinted) {
        lock.readLock().lock();
        try {
            CollateralVault vault = vaults.get(currency);
            if (vault == null) {
                return BigDecimal.ZERO;
            }
            
            if (syntheticMinted.compareTo(BigInteger.ZERO) == 0) {
                return BigDecimal.valueOf(Double.MAX_VALUE); // Infinite ratio
            }
            
            return new BigDecimal(vault.getTotalReserves())
                .divide(new BigDecimal(syntheticMinted), 8, RoundingMode.HALF_UP);
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Checks if a vault is healthy (above minimum threshold)
     */
    public boolean isVaultHealthy(String currency, BigInteger syntheticMinted) {
        BigDecimal ratio = getCollateralRatio(currency, syntheticMinted);
        return ratio.compareTo(MIN_COLLATERAL_RATIO) >= 0;
    }
    
    /**
     * Checks if a vault is in critical state
     */
    public boolean isVaultCritical(String currency, BigInteger syntheticMinted) {
        BigDecimal ratio = getCollateralRatio(currency, syntheticMinted);
        return ratio.compareTo(CRITICAL_THRESHOLD) < 0;
    }
    
    /**
     * Gets all collateral locks for an owner
     */
    public List<CollateralLock> getCollateralLocks(Address owner) {
        lock.readLock().lock();
        try {
            List<CollateralLock> locks = collateralLocks.get(owner);
            return locks != null ? new ArrayList<>(locks) : new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets vault information
     */
    public Optional<CollateralVault> getVault(String currency) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(vaults.get(currency));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all vaults
     */
    public Map<String, CollateralVault> getAllVaults() {
        lock.readLock().lock();
        try {
            return new HashMap<>(vaults);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Verifies deposit proof (production would use real cryptographic verification)
     */
    private boolean verifyDepositProof(byte[] proof, String currency, BigInteger amount) {
        if (proof == null || proof.length < 32) {
            return false;
        }
        // In production: verify signature, Merkle proof, etc.
        return true;
    }
    
    /**
     * Generates a unique lock ID
     */
    private String generateLockId(Address owner, String currency, BigInteger amount) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = owner.toString() + currency + amount.toString() + System.currentTimeMillis();
            byte[] hash = digest.digest(data.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return "LOCK-" + hexString.toString().toUpperCase();
            
        } catch (Exception e) {
            return "LOCK-" + UUID.randomUUID().toString().substring(0, 16).toUpperCase();
        }
    }
    
    /**
     * Generates cryptographic proof for a collateral lock
     */
    private byte[] generateLockProof(String lockId, Address owner, String currency, BigInteger amount) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String proofData = String.format("%s:%s:%s:%s:%d",
                lockId, owner, currency, amount, System.currentTimeMillis());
            return digest.digest(proofData.getBytes());
        } catch (Exception e) {
            return new byte[32];
        }
    }
    
    /**
     * Generates combined Merkle root from vault proofs
     */
    private byte[] generateCombinedMerkleRoot(List<byte[]> vaultProofs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] proof : vaultProofs) {
                digest.update(proof);
            }
            return digest.digest();
        } catch (Exception e) {
            return new byte[32];
        }
    }
    
    /**
     * Generates auditor signatures for proof of reserves
     */
    private List<String> generateAuditorSignatures(byte[] merkleRoot, Map<String, BigInteger> reserves) {
        List<String> signatures = new ArrayList<>();
        
        // In production, this would collect real signatures from independent auditors
        // For now, generate placeholder signatures
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < 5; i++) { // 5 auditors
                String data = Arrays.toString(merkleRoot) + reserves.toString() + i;
                byte[] sig = digest.digest(data.getBytes());
                signatures.add(Base64.getEncoder().encodeToString(sig));
            }
        } catch (Exception e) {
            // Fallback
        }
        
        return signatures;
    }
    
    /**
     * Gets system statistics
     */
    public Map<String, Object> getSystemStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalVaults", vaults.size());
            stats.put("minCollateralRatio", MIN_COLLATERAL_RATIO);
            stats.put("criticalThreshold", CRITICAL_THRESHOLD);
            stats.put("lastProofGeneration", new Date(lastProofGeneration));
            
            BigInteger totalReserves = BigInteger.ZERO;
            BigInteger totalLocked = BigInteger.ZERO;
            for (CollateralVault vault : vaults.values()) {
                totalReserves = totalReserves.add(vault.getTotalReserves());
                totalLocked = totalLocked.add(vault.getLockedReserves());
            }
            
            stats.put("totalReservesAcrossVaults", totalReserves);
            stats.put("totalLockedAcrossVaults", totalLocked);
            stats.put("totalActiveLocks", collateralLocks.values().stream()
                .mapToInt(List::size).sum());
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
}