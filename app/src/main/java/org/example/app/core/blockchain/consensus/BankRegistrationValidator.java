package org.example.app.core.blockchain.consensus;

import org.example.app.core.blockchain.transaction.BankRegistrationTransaction;
import org.example.app.core.crypto.Certificate;
import org.example.app.core.crypto.CertificateAuthority;
import org.example.app.core.types.Address;
import org.example.app.core.state.BlockchainState;

import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * BankRegistrationValidator - Validates bank registrations for the TINC system
 * 
 * Core Requirements:
 * - Validates geographic distribution (max 33% per region)
 * - Ensures government resistance through distribution
 * - Validates bank credentials and licenses
 * - Checks PKI certificate validity
 * - Prevents duplicate registrations
 * - Validates encrypted communication channels
 * - Ensures compliance with African banking standards
 */
public class BankRegistrationValidator {
    private static final Logger LOGGER = Logger.getLogger(BankRegistrationValidator.class.getName());
    
    // Geographic regions for distribution
    public enum GeographicRegion {
        WEST_AFRICA(1, "West Africa"),
        EAST_AFRICA(2, "East Africa"),
        CENTRAL_AFRICA(3, "Central Africa"),
        SOUTHERN_AFRICA(4, "Southern Africa"),
        NORTH_AFRICA(5, "North Africa"),
        NON_AFRICAN(6, "Non-African");
        
        private final int code;
        private final String name;
        
        GeographicRegion(int code, String name) {
            this.code = code;
            this.name = name;
        }
        
        public int getCode() { return code; }
        public String getName() { return name; }
        
        public static GeographicRegion fromCode(int code) {
            for (GeographicRegion region : values()) {
                if (region.code == code) return region;
            }
            return NON_AFRICAN;
        }
    }
    
    // Registration limits
    private static final double MAX_REGIONAL_SHARE = 0.33; // 33% max per region
    private static final int MIN_OPERATOR_COUNT = 1;
    private static final int MAX_OPERATOR_COUNT = 10;
    private static final int SWIFT_CODE_MIN_LENGTH = 8;
    private static final int SWIFT_CODE_MAX_LENGTH = 11;
    
    // Required credentials
    private static final Pattern SWIFT_CODE_PATTERN = Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    private static final Pattern LICENSE_ID_PATTERN = Pattern.compile("^[A-Z]{2}\\-[A-Z0-9]{6,20}$");
    
    // Dependencies
    private final BlockchainState blockchainState;
    private final CertificateAuthority certificateAuthority;
    
    // Registration tracking
    private final Map<GeographicRegion, Integer> regionCounts;
    private final Set<String> registeredSwiftCodes;
    private final Set<Address> registeredAddresses;
    private final Map<String, BankRegistrationRecord> registrationRecords;
    private int totalRegistrations;
    
    /**
     * Internal record of bank registration
     */
    private static class BankRegistrationRecord {
        final String bankId;
        final String swiftCode;
        final Address address;
        final GeographicRegion region;
        final long registrationTime;
        final Certificate certificate;
        
        BankRegistrationRecord(String bankId, String swiftCode, Address address,
                              GeographicRegion region, Certificate certificate) {
            this.bankId = bankId;
            this.swiftCode = swiftCode;
            this.address = address;
            this.region = region;
            this.registrationTime = System.currentTimeMillis();
            this.certificate = certificate;
        }
    }
    
    /**
     * Validation result
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String failureReason;
        private final Map<String, Object> metadata;
        
        private ValidationResult(boolean valid, String reason, Map<String, Object> metadata) {
            this.valid = valid;
            this.failureReason = reason;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, new HashMap<>());
        }
        
        public static ValidationResult success(Map<String, Object> metadata) {
            return new ValidationResult(true, null, metadata);
        }
        
        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason, new HashMap<>());
        }
        
        public boolean isValid() { return valid; }
        public String getFailureReason() { return failureReason; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
    
    /**
     * Constructor
     */
    public BankRegistrationValidator(BlockchainState blockchainState,
                                     CertificateAuthority certificateAuthority) {
        this.blockchainState = blockchainState;
        this.certificateAuthority = certificateAuthority;
        this.regionCounts = new ConcurrentHashMap<>();
        this.registeredSwiftCodes = ConcurrentHashMap.newKeySet();
        this.registeredAddresses = ConcurrentHashMap.newKeySet();
        this.registrationRecords = new ConcurrentHashMap<>();
        this.totalRegistrations = 0;
        
        // Initialize region counters
        for (GeographicRegion region : GeographicRegion.values()) {
            regionCounts.put(region, 0);
        }
        
        LOGGER.info("BankRegistrationValidator initialized");
    }
    
    /**
     * Main validation method for bank registration
     */
    public ValidationResult validate(BankRegistrationTransaction transaction) {
        try {
            LOGGER.info("Validating bank registration: " + transaction.getBankName());
            
            // 1. Validate transaction structure
            ValidationResult structureCheck = validateStructure(transaction);
            if (!structureCheck.isValid()) {
                return structureCheck;
            }
            
            // 2. Validate SWIFT code format
            ValidationResult swiftCheck = validateSwiftCode(transaction.getSwiftCode());
            if (!swiftCheck.isValid()) {
                return swiftCheck;
            }
            
            // 3. Validate license ID
            ValidationResult licenseCheck = validateLicenseId(transaction.getBankLicenseId());
            if (!licenseCheck.isValid()) {
                return licenseCheck;
            }
            
            // 4. Check for duplicates
            ValidationResult duplicateCheck = checkDuplicates(transaction);
            if (!duplicateCheck.isValid()) {
                return duplicateCheck;
            }
            
            // 5. Validate geographic distribution
            ValidationResult geoCheck = validateGeographicDistribution(transaction);
            if (!geoCheck.isValid()) {
                return geoCheck;
            }
            
            // 6. Validate operators
            ValidationResult operatorCheck = validateOperators(transaction);
            if (!operatorCheck.isValid()) {
                return operatorCheck;
            }
            
            // 7. Validate public key
            ValidationResult keyCheck = validatePublicKey(transaction);
            if (!keyCheck.isValid()) {
                return keyCheck;
            }
            
            // 8. Validate encrypted credentials
            ValidationResult credentialsCheck = validateEncryptedCredentials(transaction);
            if (!credentialsCheck.isValid()) {
                return credentialsCheck;
            }
            
            // 9. Validate certificate (if provided)
            if (transaction.getCertificate() != null) {
                ValidationResult certCheck = validateCertificate(transaction.getCertificate());
                if (!certCheck.isValid()) {
                    return certCheck;
                }
            }
            
            // 10. Validate country-specific requirements
            ValidationResult countryCheck = validateCountryRequirements(transaction);
            if (!countryCheck.isValid()) {
                return countryCheck;
            }
            
            LOGGER.info("Bank registration validated successfully: " + transaction.getBankName());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("bankName", transaction.getBankName());
            metadata.put("swiftCode", transaction.getSwiftCode());
            metadata.put("region", GeographicRegion.fromCode(transaction.getGeographicRegion()).getName());
            metadata.put("isCentralBank", transaction.isCentralBank());
            
            return ValidationResult.success(metadata);
            
        } catch (Exception e) {
            LOGGER.severe("Validation error: " + e.getMessage());
            return ValidationResult.failure("Validation exception: " + e.getMessage());
        }
    }
    
    /**
     * Validates basic transaction structure
     */
    private ValidationResult validateStructure(BankRegistrationTransaction transaction) {
        if (transaction == null) {
            return ValidationResult.failure("Transaction is null");
        }
        
        if (transaction.getBankName() == null || transaction.getBankName().trim().isEmpty()) {
            return ValidationResult.failure("Bank name is required");
        }
        
        if (transaction.getBankName().length() > 200) {
            return ValidationResult.failure("Bank name too long (max 200 characters)");
        }
        
        if (transaction.getBankCountry() == null || transaction.getBankCountry().trim().isEmpty()) {
            return ValidationResult.failure("Bank country is required");
        }
        
        if (transaction.getBankAddress() == null) {
            return ValidationResult.failure("Bank address is required");
        }
        
        if (transaction.getRegistrationId() == null || transaction.getRegistrationId().trim().isEmpty()) {
            return ValidationResult.failure("Registration ID is required");
        }
        
        if (transaction.getTimestamp() <= 0) {
            return ValidationResult.failure("Invalid timestamp");
        }
        
        // Check timestamp is not too far in future or past
        long now = System.currentTimeMillis();
        if (Math.abs(now - transaction.getTimestamp()) > 300000) { // 5 minutes tolerance
            return ValidationResult.failure("Registration timestamp out of acceptable range");
        }
        
        if (transaction.getGeographicRegion() < 1 || transaction.getGeographicRegion() > 6) {
            return ValidationResult.failure("Invalid geographic region code");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates SWIFT code format
     */
    private ValidationResult validateSwiftCode(String swiftCode) {
        if (swiftCode == null || swiftCode.trim().isEmpty()) {
            return ValidationResult.failure("SWIFT code is required");
        }
        
        String swift = swiftCode.trim().toUpperCase();
        
        if (swift.length() < SWIFT_CODE_MIN_LENGTH || swift.length() > SWIFT_CODE_MAX_LENGTH) {
            return ValidationResult.failure(
                String.format("SWIFT code must be between %d and %d characters",
                    SWIFT_CODE_MIN_LENGTH, SWIFT_CODE_MAX_LENGTH)
            );
        }
        
        if (!SWIFT_CODE_PATTERN.matcher(swift).matches()) {
            return ValidationResult.failure("Invalid SWIFT code format");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates bank license ID
     */
    private ValidationResult validateLicenseId(String licenseId) {
        if (licenseId == null || licenseId.trim().isEmpty()) {
            return ValidationResult.failure("Bank license ID is required");
        }
        
        String license = licenseId.trim().toUpperCase();
        
        if (!LICENSE_ID_PATTERN.matcher(license).matches()) {
            return ValidationResult.failure(
                "Invalid license ID format (expected: XX-XXXXXX where X is alphanumeric)"
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Checks for duplicate registrations
     */
    private ValidationResult checkDuplicates(BankRegistrationTransaction transaction) {
        // Check SWIFT code
        if (registeredSwiftCodes.contains(transaction.getSwiftCode())) {
            return ValidationResult.failure("SWIFT code already registered: " + transaction.getSwiftCode());
        }
        
        // Check address
        if (registeredAddresses.contains(transaction.getBankAddress())) {
            return ValidationResult.failure("Bank address already registered: " + transaction.getBankAddress());
        }
        
        // Check registration ID
        if (registrationRecords.containsKey(transaction.getRegistrationId())) {
            return ValidationResult.failure("Registration ID already exists: " + transaction.getRegistrationId());
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates geographic distribution (33% rule)
     */
    private ValidationResult validateGeographicDistribution(BankRegistrationTransaction transaction) {
        GeographicRegion region = GeographicRegion.fromCode(transaction.getGeographicRegion());
        int currentCount = regionCounts.get(region);
        int totalAfterRegistration = totalRegistrations + 1;
        
        // Calculate what percentage this region would have after registration
        double regionPercentage = (double)(currentCount + 1) / totalAfterRegistration;
        
        if (regionPercentage > MAX_REGIONAL_SHARE) {
            return ValidationResult.failure(
                String.format("Geographic distribution limit exceeded for %s. " +
                             "Current: %.2f%%, Max allowed: %.2f%%",
                    region.getName(),
                    regionPercentage * 100,
                    MAX_REGIONAL_SHARE * 100)
            );
        }
        
        LOGGER.fine(String.format("Geographic check passed for %s: %.2f%%",
            region.getName(), regionPercentage * 100));
        
        return ValidationResult.success();
    }
    
    /**
     * Validates bank operators
     */
    private ValidationResult validateOperators(BankRegistrationTransaction transaction) {
        List<Address> operators = transaction.getInitialOperators();
        
        if (operators == null || operators.isEmpty()) {
            return ValidationResult.failure("At least one operator is required");
        }
        
        if (operators.size() < MIN_OPERATOR_COUNT) {
            return ValidationResult.failure("Minimum " + MIN_OPERATOR_COUNT + " operator(s) required");
        }
        
        if (operators.size() > MAX_OPERATOR_COUNT) {
            return ValidationResult.failure("Maximum " + MAX_OPERATOR_COUNT + " operators allowed");
        }
        
        // Check for duplicate operators
        Set<Address> uniqueOperators = new HashSet<>(operators);
        if (uniqueOperators.size() != operators.size()) {
            return ValidationResult.failure("Duplicate operators detected");
        }
        
        // Validate each operator address
        for (Address operator : operators) {
            if (operator == null || operator.toString().trim().isEmpty()) {
                return ValidationResult.failure("Invalid operator address");
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates public key
     */
    private ValidationResult validatePublicKey(BankRegistrationTransaction transaction) {
        byte[] publicKey = transaction.getPublicKey();
        
        if (publicKey == null || publicKey.length == 0) {
            return ValidationResult.failure("Public key is required");
        }
        
        // Validate key length (typical RSA 2048-bit = 294 bytes, 4096-bit = 550 bytes)
        if (publicKey.length < 200 || publicKey.length > 1000) {
            return ValidationResult.failure("Invalid public key length");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates encrypted credentials
     */
    private ValidationResult validateEncryptedCredentials(BankRegistrationTransaction transaction) {
        byte[] credentials = transaction.getEncryptedCredentials();
        
        if (credentials == null || credentials.length == 0) {
            return ValidationResult.failure("Encrypted credentials are required");
        }
        
        // Validate minimum credential size (should contain encrypted data)
        if (credentials.length < 64) {
            return ValidationResult.failure("Encrypted credentials appear invalid (too short)");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates certificate
     */
    private ValidationResult validateCertificate(Certificate certificate) {
        if (certificate == null) {
            return ValidationResult.failure("Certificate is null");
        }
        
        // Verify certificate with CA
        try {
            boolean isValid = certificateAuthority.verifyCertificate(certificate);
            if (!isValid) {
                return ValidationResult.failure("Certificate verification failed");
            }
        } catch (Exception e) {
            return ValidationResult.failure("Certificate validation error: " + e.getMessage());
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates country-specific requirements
     */
    private ValidationResult validateCountryRequirements(BankRegistrationTransaction transaction) {
        String country = transaction.getBankCountry();
        
        // Validate country code format (ISO 3166-1 alpha-2)
        if (country.length() != 2 || !country.matches("[A-Z]{2}")) {
            return ValidationResult.failure("Invalid country code format (expected ISO 3166-1 alpha-2)");
        }
        
        // Central banks have additional requirements
        if (transaction.isCentralBank()) {
            // Only one central bank per country
            boolean hasCentralBank = registrationRecords.values().stream()
                .anyMatch(record -> 
                    record.bankId.startsWith("CENTRAL_" + country)
                );
            
            if (hasCentralBank) {
                return ValidationResult.failure("Central bank already registered for country: " + country);
            }
            
            // Central bank registration ID should follow specific format
            if (!transaction.getRegistrationId().startsWith("CENTRAL_" + country)) {
                return ValidationResult.failure("Invalid registration ID format for central bank");
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Records a successful registration
     */
    public void recordRegistration(BankRegistrationTransaction transaction, Certificate certificate) {
        GeographicRegion region = GeographicRegion.fromCode(transaction.getGeographicRegion());
        
        BankRegistrationRecord record = new BankRegistrationRecord(
            transaction.getRegistrationId(),
            transaction.getSwiftCode(),
            transaction.getBankAddress(),
            region,
            certificate
        );
        
        registrationRecords.put(transaction.getRegistrationId(), record);
        registeredSwiftCodes.add(transaction.getSwiftCode());
        registeredAddresses.add(transaction.getBankAddress());
        
        regionCounts.merge(region, 1, Integer::sum);
        totalRegistrations++;
        
        LOGGER.info(String.format("Registration recorded: %s (%s) in %s",
            transaction.getBankName(), transaction.getSwiftCode(), region.getName()));
    }
    
    /**
     * Gets geographic distribution statistics
     */
    public Map<String, Object> getDistributionStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalRegistrations", totalRegistrations);
        
        Map<String, Double> regionDistribution = new HashMap<>();
        for (Map.Entry<GeographicRegion, Integer> entry : regionCounts.entrySet()) {
            double percentage = totalRegistrations > 0 ?
                (double)entry.getValue() / totalRegistrations : 0.0;
            regionDistribution.put(entry.getKey().getName(), percentage);
        }
        stats.put("regionDistribution", regionDistribution);
        
        // Check if system is government-resistant
        boolean isResistant = regionDistribution.values().stream()
            .allMatch(percentage -> percentage <= MAX_REGIONAL_SHARE);
        stats.put("isGovernmentResistant", isResistant);
        
        return stats;
    }
    
    /**
     * Checks if a bank is registered
     */
    public boolean isBankRegistered(String swiftCode) {
        return registeredSwiftCodes.contains(swiftCode);
    }
    
    /**
     * Checks if an address is registered
     */
    public boolean isAddressRegistered(Address address) {
        return registeredAddresses.contains(address);
    }
    
    /**
     * Gets registration record
     */
    public BankRegistrationRecord getRegistrationRecord(String registrationId) {
        return registrationRecords.get(registrationId);
    }
    
    /**
     * Gets all registration records
     */
    public Collection<BankRegistrationRecord> getAllRegistrations() {
        return new ArrayList<>(registrationRecords.values());
    }
}