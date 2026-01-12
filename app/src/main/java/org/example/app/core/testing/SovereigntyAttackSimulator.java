package org.example.app.core.testing;

import org.example.app.core.scaling.SovereigntyEnforcer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * SovereigntyAttackSimulator tests resistance to sovereignty attacks.
 * 
 * Attack scenarios:
 * 1. Single country control attempt (>33%)
 * 2. Single region dominance attempt (>33%)
 * 3. Single entity control attempt (>33%)
 * 4. Cloud provider takeover attempt (>20%)
 * 5. Community ownership dilution (<50%)
 * 
 * Validates that:
 * - System rejects validator registrations that violate sovereignty
 * - Circuit breakers activate when limits approached
 * - Geographic distribution maintained under attack
 * - No single point of control possible
 */
public class SovereigntyAttackSimulator {
    private static final Logger logger = Logger.getLogger(SovereigntyAttackSimulator.class.getName());
    
    private final SovereigntyEnforcer sovereigntyEnforcer;
    private final List<AttackResult> attackResults;
    
    public SovereigntyAttackSimulator(SovereigntyEnforcer sovereigntyEnforcer) {
        this.sovereigntyEnforcer = sovereigntyEnforcer;
        this.attackResults = new ArrayList<>();
        
        logger.info("SovereigntyAttackSimulator initialized");
    }
    
    /**
     * Simulate single country control attack
     */
    public AttackResult simulateCountryControlAttack(String targetCountry, int attemptedValidators) {
        logger.info("Simulating country control attack: " + targetCountry + 
            " attempting " + attemptedValidators + " validators");
        
        long startTime = System.currentTimeMillis();
        int accepted = 0;
        int rejected = 0;
        
        for (int i = 0; i < attemptedValidators; i++) {
            String validatorId = "ATTACK_" + targetCountry + "_VAL_" + i;
            
            boolean registered = sovereigntyEnforcer.registerValidator(
                validatorId,
                targetCountry,
                "REGION_" + targetCountry.substring(0, 1),
                "ENTITY_ATTACKER",
                false // Not community-owned
            );
            
            if (registered) {
                accepted++;
            } else {
                rejected++;
            }
        }
        
        long endTime = System.currentTimeMillis();
        
        // Check if sovereignty maintained
        double countryControl = sovereigntyEnforcer.getCountryControl(targetCountry);
        boolean attackBlocked = countryControl <= 0.33;
        
        AttackResult result = new AttackResult(
            "Country Control Attack",
            targetCountry,
            attemptedValidators,
            accepted,
            rejected,
            attackBlocked,
            countryControl,
            endTime - startTime
        );
        
        attackResults.add(result);
        
        logger.info("Country attack result: " + result);
        return result;
    }
    
    /**
     * Simulate region dominance attack
     */
    public AttackResult simulateRegionDominanceAttack(String targetRegion, int attemptedValidators) {
        logger.info("Simulating region dominance attack: " + targetRegion);
        
        long startTime = System.currentTimeMillis();
        int accepted = 0;
        int rejected = 0;
        
        // Try to register validators in multiple countries within the region
        String[] regionCountries = getCountriesInRegion(targetRegion);
        
        for (int i = 0; i < attemptedValidators; i++) {
            String country = regionCountries[i % regionCountries.length];
            String validatorId = "ATTACK_" + targetRegion + "_VAL_" + i;
            
            boolean registered = sovereigntyEnforcer.registerValidator(
                validatorId,
                country,
                targetRegion,
                "ENTITY_REGION_ATTACKER",
                false
            );
            
            if (registered) {
                accepted++;
            } else {
                rejected++;
            }
        }
        
        long endTime = System.currentTimeMillis();
        
        // Check if sovereignty maintained
        double regionControl = sovereigntyEnforcer.getRegionControl(targetRegion);
        boolean attackBlocked = regionControl <= 0.33;
        
        AttackResult result = new AttackResult(
            "Region Dominance Attack",
            targetRegion,
            attemptedValidators,
            accepted,
            rejected,
            attackBlocked,
            regionControl,
            endTime - startTime
        );
        
        attackResults.add(result);
        
        logger.info("Region attack result: " + result);
        return result;
    }
    
    /**
     * Simulate entity control attack
     */
    public AttackResult simulateEntityControlAttack(String attackerEntity, int attemptedValidators) {
        logger.info("Simulating entity control attack: " + attackerEntity);
        
        long startTime = System.currentTimeMillis();
        int accepted = 0;
        int rejected = 0;
        
        // Try to register validators across multiple countries/regions
        String[] countries = {"NGA", "KEN", "ZAF", "EGY", "GHA", "ETH"};
        String[] regions = {"WEST_AFRICA", "EAST_AFRICA", "SOUTHERN_AFRICA", "NORTH_AFRICA"};
        
        for (int i = 0; i < attemptedValidators; i++) {
            String country = countries[i % countries.length];
            String region = regions[i % regions.length];
            String validatorId = "ATTACK_" + attackerEntity + "_VAL_" + i;
            
            boolean registered = sovereigntyEnforcer.registerValidator(
                validatorId,
                country,
                region,
                attackerEntity,
                false
            );
            
            if (registered) {
                accepted++;
            } else {
                rejected++;
            }
        }
        
        long endTime = System.currentTimeMillis();
        
        // Check if sovereignty maintained
        double entityControl = sovereigntyEnforcer.getEntityControl(attackerEntity);
        boolean attackBlocked = entityControl <= 0.33;
        
        AttackResult result = new AttackResult(
            "Entity Control Attack",
            attackerEntity,
            attemptedValidators,
            accepted,
            rejected,
            attackBlocked,
            entityControl,
            endTime - startTime
        );
        
        attackResults.add(result);
        
        logger.info("Entity attack result: " + result);
        return result;
    }
    
    /**
     * Simulate cloud provider takeover
     */
    public AttackResult simulateCloudTakeoverAttack(int attemptedCloudValidators) {
        logger.info("Simulating cloud provider takeover attack");
        
        long startTime = System.currentTimeMillis();
        int accepted = 0;
        int rejected = 0;
        
        String[] cloudProviders = {"AWS", "AZURE", "GOOGLE_CLOUD"};
        String[] countries = {"NGA", "KEN", "ZAF", "EGY", "GHA"};
        
        for (int i = 0; i < attemptedCloudValidators; i++) {
            String provider = cloudProviders[i % cloudProviders.length];
            String country = countries[i % countries.length];
            String validatorId = "CLOUD_ATTACK_VAL_" + i;
            
            // Try to register as non-community (cloud-hosted)
            boolean registered = sovereigntyEnforcer.registerValidator(
                validatorId,
                country,
                "WEST_AFRICA",
                "ENTITY_" + provider,
                false // Cloud-hosted, not community
            );
            
            if (registered) {
                accepted++;
            } else {
                rejected++;
            }
        }
        
        long endTime = System.currentTimeMillis();
        
        // Check cloud dependency
        SovereigntyEnforcer.SovereigntyReport report = sovereigntyEnforcer.getComplianceReport();
        double cloudDependency = report.getCloudDependencyPercentage();
        boolean attackBlocked = cloudDependency <= 0.20;
        
        AttackResult result = new AttackResult(
            "Cloud Takeover Attack",
            "CLOUD_PROVIDERS",
            attemptedCloudValidators,
            accepted,
            rejected,
            attackBlocked,
            cloudDependency,
            endTime - startTime
        );
        
        attackResults.add(result);
        
        logger.info("Cloud attack result: " + result);
        return result;
    }
    
    /**
     * Simulate community ownership dilution attack
     */
    public AttackResult simulateCommunityDilutionAttack(int attemptedCorporateValidators) {
        logger.info("Simulating community ownership dilution attack");
        
        long startTime = System.currentTimeMillis();
        int accepted = 0;
        int rejected = 0;
        
        for (int i = 0; i < attemptedCorporateValidators; i++) {
            String validatorId = "CORPORATE_VAL_" + i;
            String country = "NGA"; // Nigeria
            
            // Try to register as non-community validator
            boolean registered = sovereigntyEnforcer.registerValidator(
                validatorId,
                country,
                "WEST_AFRICA",
                "ENTITY_CORPORATION",
                false // Not community-owned
            );
            
            if (registered) {
                accepted++;
            } else {
                rejected++;
            }
        }
        
        long endTime = System.currentTimeMillis();
        
        // Check community ownership
        SovereigntyEnforcer.SovereigntyReport report = sovereigntyEnforcer.getComplianceReport();
        double communityOwnership = report.getCommunityOwnershipPercentage();
        boolean attackBlocked = communityOwnership >= 0.50;
        
        AttackResult result = new AttackResult(
            "Community Dilution Attack",
            "CORPORATE_ENTITIES",
            attemptedCorporateValidators,
            accepted,
            rejected,
            attackBlocked,
            1.0 - communityOwnership, // Inverse for "attack strength"
            endTime - startTime
        );
        
        attackResults.add(result);
        
        logger.info("Community dilution result: " + result);
        return result;
    }
    
    /**
     * Run comprehensive attack simulation suite
     */
    public AttackSuiteResult runComprehensiveAttackSuite() {
        logger.info("Running comprehensive sovereignty attack suite...");
        
        long startTime = System.currentTimeMillis();
        
        // Test 1: Country control attacks
        List<AttackResult> countryAttacks = new ArrayList<>();
        countryAttacks.add(simulateCountryControlAttack("NGA", 100)); // Nigeria
        countryAttacks.add(simulateCountryControlAttack("ZAF", 100)); // South Africa
        countryAttacks.add(simulateCountryControlAttack("EGY", 100)); // Egypt
        
        // Test 2: Region dominance attacks
        List<AttackResult> regionAttacks = new ArrayList<>();
        regionAttacks.add(simulateRegionDominanceAttack("WEST_AFRICA", 150));
        regionAttacks.add(simulateRegionDominanceAttack("EAST_AFRICA", 150));
        
        // Test 3: Entity control attack
        AttackResult entityAttack = simulateEntityControlAttack("MALICIOUS_ENTITY", 200);
        
        // Test 4: Cloud takeover attack
        AttackResult cloudAttack = simulateCloudTakeoverAttack(100);
        
        // Test 5: Community dilution attack
        AttackResult communityAttack = simulateCommunityDilutionAttack(150);
        
        long endTime = System.currentTimeMillis();
        
        // Aggregate results
        int totalAttacksBlocked = 0;
        int totalAttacks = countryAttacks.size() + regionAttacks.size() + 3; // +3 for entity, cloud, community
        
        for (AttackResult result : countryAttacks) {
            if (result.isAttackBlocked()) totalAttacksBlocked++;
        }
        for (AttackResult result : regionAttacks) {
            if (result.isAttackBlocked()) totalAttacksBlocked++;
        }
        if (entityAttack.isAttackBlocked()) totalAttacksBlocked++;
        if (cloudAttack.isAttackBlocked()) totalAttacksBlocked++;
        if (communityAttack.isAttackBlocked()) totalAttacksBlocked++;
        
        AttackSuiteResult suiteResult = new AttackSuiteResult(
            totalAttacks,
            totalAttacksBlocked,
            endTime - startTime
        );
        
        logger.info("Attack suite completed: " + suiteResult);
        return suiteResult;
    }
    
    /**
     * Get countries in region
     */
    private String[] getCountriesInRegion(String region) {
        switch (region) {
            case "WEST_AFRICA":
                return new String[]{"NGA", "GHA", "CIV", "SEN", "MLI"};
            case "EAST_AFRICA":
                return new String[]{"KEN", "TZA", "UGA", "ETH", "RWA"};
            case "SOUTHERN_AFRICA":
                return new String[]{"ZAF", "ZWE", "BWA", "NAM", "ZMB"};
            case "NORTH_AFRICA":
                return new String[]{"EGY", "LBY", "TUN", "DZA", "MAR"};
            case "CENTRAL_AFRICA":
                return new String[]{"COD", "CMR", "CAF", "TCD", "COG"};
            default:
                return new String[]{"NGA", "KEN", "ZAF"};
        }
    }
    
    /**
     * Get all attack results
     */
    public List<AttackResult> getAllAttackResults() {
        return new ArrayList<>(attackResults);
    }
    
    /**
     * Attack result
     */
    public static class AttackResult {
        private final String attackType;
        private final String target;
        private final int attemptedValidators;
        private final int acceptedValidators;
        private final int rejectedValidators;
        private final boolean attackBlocked;
        private final double finalControlPercentage;
        private final long durationMs;
        
        public AttackResult(String attackType, String target, int attempted, int accepted,
                          int rejected, boolean blocked, double control, long duration) {
            this.attackType = attackType;
            this.target = target;
            this.attemptedValidators = attempted;
            this.acceptedValidators = accepted;
            this.rejectedValidators = rejected;
            this.attackBlocked = blocked;
            this.finalControlPercentage = control;
            this.durationMs = duration;
        }
        
        public String getAttackType() { return attackType; }
        public boolean isAttackBlocked() { return attackBlocked; }
        public double getFinalControlPercentage() { return finalControlPercentage; }
        
        @Override
        public String toString() {
            return String.format(
                "%s on %s: Attempted=%d, Accepted=%d, Rejected=%d, " +
                "Final Control=%.2f%%, Attack Blocked=%s",
                attackType, target, attemptedValidators, acceptedValidators,
                rejectedValidators, finalControlPercentage * 100, 
                attackBlocked ? "YES ✓" : "NO ✗"
            );
        }
    }
    
    /**
     * Attack suite result
     */
    public static class AttackSuiteResult {
        private final int totalAttacks;
        private final int attacksBlocked;
        private final long durationMs;
        
        public AttackSuiteResult(int total, int blocked, long duration) {
            this.totalAttacks = total;
            this.attacksBlocked = blocked;
            this.durationMs = duration;
        }
        
        public int getTotalAttacks() { return totalAttacks; }
        public int getAttacksBlocked() { return attacksBlocked; }
        public double getBlockRate() {
            return totalAttacks > 0 ? (double) attacksBlocked / totalAttacks : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Attack Suite: Total=%d, Blocked=%d (%.2f%%), Duration=%.2fs",
                totalAttacks, attacksBlocked, getBlockRate() * 100, durationMs / 1000.0
            );
        }
    }
}
