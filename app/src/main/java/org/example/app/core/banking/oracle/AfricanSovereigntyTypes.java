package org.example.app.core.banking.oracle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * AfricanSovereigntyTypes - Shared types for African Node Sovereignty system
 * 
 * Contains geographic enums, node classifications, and committee structures
 * used across all oracle components to enforce:
 * - 35-node African DA committee (7 per region)
 * - Geographic distribution limits (max 33%)
 * - Community ownership targets (50%+ community, max 20% cloud)
 * - Byzantine Fault Tolerance (5/7 consensus per region)
 * 
 * CRITICAL: These types enforce government-resistance and decentralization
 */
public class AfricanSovereigntyTypes {
    private static final Logger LOGGER = Logger.getLogger(AfricanSovereigntyTypes.class.getName());
    
    /**
     * African geographic regions for distribution enforcement
     * Each region has 7 nodes in the Data Availability Committee
     */
    public enum AfricanRegion {
        NORTH_AFRICA("North Africa", Arrays.asList(
            "Morocco", "Egypt", "Algeria", "Tunisia", "Libya", "Sudan", "Mauritania"
        )),
        WEST_AFRICA("West Africa", Arrays.asList(
            "Nigeria", "Ghana", "Senegal", "Ivory Coast", "Mali", "Benin", "Togo", 
            "Burkina Faso", "Niger", "Guinea", "Sierra Leone", "Liberia"
        )),
        EAST_AFRICA("East Africa", Arrays.asList(
            "Kenya", "Ethiopia", "Tanzania", "Uganda", "Rwanda", "Somalia", "Djibouti",
            "Eritrea", "Burundi", "South Sudan"
        )),
        SOUTH_AFRICA("Southern Africa", Arrays.asList(
            "South Africa", "Botswana", "Namibia", "Zimbabwe", "Zambia", "Angola",
            "Mozambique", "Lesotho", "Eswatini", "Malawi"
        )),
        CENTRAL_AFRICA("Central Africa", Arrays.asList(
            "Cameroon", "Democratic Republic of Congo", "Gabon", "Chad", "Congo",
            "Central African Republic", "Equatorial Guinea", "Sao Tome and Principe"
        ));
        
        private final String displayName;
        private final List<String> countries;
        public static final int NODES_PER_REGION = 7;
        public static final int TOTAL_REGIONS = 5;
        public static final int TOTAL_NODES = 35; // 5 regions × 7 nodes
        
        AfricanRegion(String displayName, List<String> countries) {
            this.displayName = displayName;
            this.countries = countries;
        }
        
        public String getDisplayName() { return displayName; }
        public List<String> getCountries() { return new ArrayList<>(countries); }
        
        /**
         * Gets region for a given country
         */
        public static AfricanRegion fromCountry(String country) {
            for (AfricanRegion region : values()) {
                if (region.countries.stream().anyMatch(c -> c.equalsIgnoreCase(country))) {
                    return region;
                }
            }
            throw new IllegalArgumentException("Country not found in African regions: " + country);
        }
        
        /**
         * Checks if country is in Africa
         */
        public static boolean isAfricanCountry(String country) {
            try {
                fromCountry(country);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }
    
    /**
     * Node ownership type for community ownership enforcement
     */
    public enum NodeType {
        COMMUNITY("Community", "Individual/cooperative owned nodes"),
        INSTITUTIONAL("Institutional", "Banks, universities, NGOs"),
        CLOUD("Cloud", "Cloud providers (AWS, Azure, GCP) - max 20% total");
        
        private final String displayName;
        private final String description;
        
        NodeType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * Source geography classification
     */
    public enum SourceGeography {
        AFRICAN("African", true),
        NON_AFRICAN("Non-African", false);
        
        private final String displayName;
        private final boolean isAfrican;
        
        SourceGeography(String displayName, boolean isAfrican) {
            this.displayName = displayName;
            this.isAfrican = isAfrican;
        }
        
        public String getDisplayName() { return displayName; }
        public boolean isAfrican() { return isAfrican; }
    }
    
    /**
     * Node information for tracking in 35-node committee
     */
    public static class NodeInfo {
        private final String nodeId;
        private final AfricanRegion region;
        private final String country;
        private final NodeType nodeType;
        private final boolean isCommunityOwned;
        private final String owner;
        private BigDecimal stakeAmount;
        private boolean isActive;
        private long registrationTime;
        private long lastActivityTime;
        
        public NodeInfo(String nodeId, AfricanRegion region, String country, 
                       NodeType nodeType, boolean isCommunityOwned, String owner) {
            this.nodeId = nodeId;
            this.region = region;
            this.country = country;
            this.nodeType = nodeType;
            this.isCommunityOwned = isCommunityOwned;
            this.owner = owner;
            this.stakeAmount = BigDecimal.ZERO;
            this.isActive = false;
            this.registrationTime = System.currentTimeMillis();
            this.lastActivityTime = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public AfricanRegion getRegion() { return region; }
        public String getCountry() { return country; }
        public NodeType getNodeType() { return nodeType; }
        public boolean isCommunityOwned() { return isCommunityOwned; }
        public String getOwner() { return owner; }
        public BigDecimal getStakeAmount() { return stakeAmount; }
        public boolean isActive() { return isActive; }
        public long getRegistrationTime() { return registrationTime; }
        public long getLastActivityTime() { return lastActivityTime; }
        
        public void setStakeAmount(BigDecimal amount) { this.stakeAmount = amount; }
        public void setActive(boolean active) { this.isActive = active; }
        public void updateActivity() { this.lastActivityTime = System.currentTimeMillis(); }
        
        @Override
        public String toString() {
            return String.format("Node{id='%s', region=%s, country='%s', type=%s, community=%s}",
                nodeId, region, country, nodeType, isCommunityOwned);
        }
    }
    
    /**
     * Regional committee for Byzantine Fault Tolerant consensus
     * Requires 5 of 7 nodes to agree (BFT threshold)
     */
    public static class RegionalCommittee {
        private final AfricanRegion region;
        private final Map<String, NodeInfo> committeeNodes;
        private final int bftThreshold;
        private BigDecimal aggregateWeight;
        
        public static final int BFT_THRESHOLD = 5; // Need 5 of 7
        
        public RegionalCommittee(AfricanRegion region) {
            this.region = region;
            this.committeeNodes = new ConcurrentHashMap<>();
            this.bftThreshold = BFT_THRESHOLD;
            this.aggregateWeight = BigDecimal.ZERO;
        }
        
        /**
         * Registers a node to this regional committee
         */
        public boolean registerNode(NodeInfo node) {
            if (committeeNodes.size() >= AfricanRegion.NODES_PER_REGION) {
                LOGGER.warning("Regional committee full: " + region);
                return false;
            }
            
            if (!node.getRegion().equals(region)) {
                LOGGER.warning("Node region mismatch: " + node.getRegion() + " vs " + region);
                return false;
            }
            
            committeeNodes.put(node.getNodeId(), node);
            LOGGER.info(String.format("Node registered to %s committee: %s", region, node.getNodeId()));
            
            return true;
        }
        
        /**
         * Checks if committee has Byzantine Fault Tolerant consensus
         */
        public boolean hasBFTConsensus() {
            long activeNodes = committeeNodes.values().stream()
                .filter(NodeInfo::isActive)
                .count();
            
            return activeNodes >= bftThreshold;
        }
        
        /**
         * Gets consensus from committee (5 of 7 must agree)
         */
        public <T> T getConsensus(Map<String, T> nodeResponses, Class<T> clazz) {
            if (nodeResponses.size() < bftThreshold) {
                return null;
            }
            
            // Find most common response
            Map<T, Long> responseCounts = new HashMap<>();
            for (T response : nodeResponses.values()) {
                responseCounts.put(response, responseCounts.getOrDefault(response, 0L) + 1);
            }
            
            // Need at least BFT threshold agreeing
            for (Map.Entry<T, Long> entry : responseCounts.entrySet()) {
                if (entry.getValue() >= bftThreshold) {
                    return entry.getKey();
                }
            }
            
            return null; // No consensus reached
        }
        
        public AfricanRegion getRegion() { return region; }
        public Map<String, NodeInfo> getCommitteeNodes() { return new HashMap<>(committeeNodes); }
        public int getBftThreshold() { return bftThreshold; }
        public int getActiveNodeCount() {
            return (int) committeeNodes.values().stream().filter(NodeInfo::isActive).count();
        }
        public boolean isFull() { return committeeNodes.size() >= AfricanRegion.NODES_PER_REGION; }
        
        @Override
        public String toString() {
            return String.format("Committee{region=%s, nodes=%d/%d, active=%d, BFT=%s}",
                region, committeeNodes.size(), AfricanRegion.NODES_PER_REGION,
                getActiveNodeCount(), hasBFTConsensus() ? "YES" : "NO");
        }
    }
    
    /**
     * Geographic distribution validator
     * Enforces max 33% from any single region or country
     */
    public static class GeographicDistributionValidator {
        private static final BigDecimal MAX_REGION_PERCENTAGE = new BigDecimal("0.33");
        private static final BigDecimal MAX_COUNTRY_PERCENTAGE = new BigDecimal("0.33");
        private static final int MIN_REGIONS_REQUIRED = 3;
        
        /**
         * Validates that no single region exceeds 33% of total
         */
        public static boolean validateRegionalDistribution(Map<AfricanRegion, BigDecimal> regionalValues) {
            BigDecimal total = regionalValues.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            if (total.compareTo(BigDecimal.ZERO) == 0) {
                return true; // Empty is valid
            }
            
            for (Map.Entry<AfricanRegion, BigDecimal> entry : regionalValues.entrySet()) {
                BigDecimal percentage = entry.getValue().divide(total, 4, RoundingMode.HALF_UP);
                
                if (percentage.compareTo(MAX_REGION_PERCENTAGE) > 0) {
                    LOGGER.warning(String.format("Region %s exceeds 33%% limit: %.2f%%",
                        entry.getKey(), percentage.multiply(new BigDecimal("100"))));
                    return false;
                }
            }
            
            return true;
        }
        
        /**
         * Validates that no single country exceeds 33% of total
         */
        public static boolean validateCountryDistribution(Map<String, BigDecimal> countryValues) {
            BigDecimal total = countryValues.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            if (total.compareTo(BigDecimal.ZERO) == 0) {
                return true;
            }
            
            for (Map.Entry<String, BigDecimal> entry : countryValues.entrySet()) {
                BigDecimal percentage = entry.getValue().divide(total, 4, RoundingMode.HALF_UP);
                
                if (percentage.compareTo(MAX_COUNTRY_PERCENTAGE) > 0) {
                    LOGGER.warning(String.format("Country %s exceeds 33%% limit: %.2f%%",
                        entry.getKey(), percentage.multiply(new BigDecimal("100"))));
                    return false;
                }
            }
            
            return true;
        }
        
        /**
         * Validates minimum regional diversity (at least 3 regions)
         */
        public static boolean validateMinimumRegions(Set<AfricanRegion> regions) {
            if (regions.size() < MIN_REGIONS_REQUIRED) {
                LOGGER.warning(String.format("Insufficient regions: %d (minimum: %d)",
                    regions.size(), MIN_REGIONS_REQUIRED));
                return false;
            }
            return true;
        }
        
        /**
         * Validates node type distribution (50%+ community, max 20% cloud)
         */
        public static boolean validateNodeTypeDistribution(Map<NodeType, Long> nodeTypeCounts) {
            long total = nodeTypeCounts.values().stream().reduce(0L, Long::sum);
            
            if (total == 0) {
                return true;
            }
            
            long communityCount = nodeTypeCounts.getOrDefault(NodeType.COMMUNITY, 0L);
            long cloudCount = nodeTypeCounts.getOrDefault(NodeType.CLOUD, 0L);
            
            BigDecimal communityPercentage = new BigDecimal(communityCount)
                .divide(new BigDecimal(total), 4, RoundingMode.HALF_UP);
            BigDecimal cloudPercentage = new BigDecimal(cloudCount)
                .divide(new BigDecimal(total), 4, RoundingMode.HALF_UP);
            
            // Community should be 50%+ (warning if below, not error)
            if (communityPercentage.compareTo(new BigDecimal("0.50")) < 0) {
                LOGGER.warning(String.format("Community ownership below 50%% target: %.2f%%",
                    communityPercentage.multiply(new BigDecimal("100"))));
            }
            
            // Cloud must be below 20% (hard limit)
            if (cloudPercentage.compareTo(new BigDecimal("0.20")) > 0) {
                LOGGER.severe(String.format("Cloud nodes exceed 20%% limit: %.2f%%",
                    cloudPercentage.multiply(new BigDecimal("100"))));
                return false;
            }
            
            return true;
        }
    }
    
    /**
     * Circuit breaker for detecting and preventing data manipulation
     */
    public static class CircuitBreaker {
        private static final BigDecimal MAX_DEVIATION_THRESHOLD = new BigDecimal("0.05"); // 5%
        private static final int CONSECUTIVE_VIOLATIONS_LIMIT = 3;
        
        private final String identifier;
        private final Map<String, List<DeviationEvent>> deviationHistory;
        private boolean isTripped;
        private long trippedTime;
        
        public CircuitBreaker(String identifier) {
            this.identifier = identifier;
            this.deviationHistory = new ConcurrentHashMap<>();
            this.isTripped = false;
            this.trippedTime = 0;
        }
        
        /**
         * Checks if value deviates too much from previous
         */
        public boolean checkDeviation(String dataKey, BigDecimal newValue, BigDecimal previousValue) {
            if (previousValue.compareTo(BigDecimal.ZERO) == 0) {
                return true; // Can't calculate deviation from zero
            }
            
            BigDecimal deviation = newValue.subtract(previousValue).abs()
                .divide(previousValue, 4, RoundingMode.HALF_UP);
            
            if (deviation.compareTo(MAX_DEVIATION_THRESHOLD) > 0) {
                recordDeviation(dataKey, deviation, newValue, previousValue);
                
                if (hasConsecutiveViolations(dataKey)) {
                    tripCircuitBreaker(dataKey);
                    return false;
                }
            } else {
                // Clear history on good data
                clearDeviationHistory(dataKey);
            }
            
            return !isTripped;
        }
        
        private void recordDeviation(String dataKey, BigDecimal deviation, 
                                    BigDecimal newValue, BigDecimal previousValue) {
            DeviationEvent event = new DeviationEvent(dataKey, deviation, newValue, 
                previousValue, System.currentTimeMillis());
            
            deviationHistory.computeIfAbsent(dataKey, k -> new ArrayList<>()).add(event);
            
            LOGGER.warning(String.format("Deviation detected: %s - %.2f%% (new: %s, prev: %s)",
                dataKey, deviation.multiply(new BigDecimal("100")), newValue, previousValue));
        }
        
        private boolean hasConsecutiveViolations(String dataKey) {
            List<DeviationEvent> events = deviationHistory.get(dataKey);
            if (events == null || events.size() < CONSECUTIVE_VIOLATIONS_LIMIT) {
                return false;
            }
            
            // Check last N events are all violations
            int recentViolations = Math.min(events.size(), CONSECUTIVE_VIOLATIONS_LIMIT);
            List<DeviationEvent> recent = events.subList(
                events.size() - recentViolations, events.size());
            
            return recent.size() >= CONSECUTIVE_VIOLATIONS_LIMIT;
        }
        
        private void tripCircuitBreaker(String dataKey) {
            isTripped = true;
            trippedTime = System.currentTimeMillis();
            
            LOGGER.severe(String.format("⚠️ CIRCUIT BREAKER TRIPPED: %s - %s - Consecutive violations: %d",
                identifier, dataKey, CONSECUTIVE_VIOLATIONS_LIMIT));
        }
        
        public void reset() {
            isTripped = false;
            trippedTime = 0;
            deviationHistory.clear();
            LOGGER.info("Circuit breaker reset: " + identifier);
        }
        
        private void clearDeviationHistory(String dataKey) {
            deviationHistory.remove(dataKey);
        }
        
        public boolean isTripped() { return isTripped; }
        public long getTrippedTime() { return trippedTime; }
        
        /**
         * Deviation event record
         */
        public static class DeviationEvent {
            private final String dataKey;
            private final BigDecimal deviation;
            private final BigDecimal newValue;
            private final BigDecimal previousValue;
            private final long timestamp;
            
            public DeviationEvent(String dataKey, BigDecimal deviation, BigDecimal newValue,
                                BigDecimal previousValue, long timestamp) {
                this.dataKey = dataKey;
                this.deviation = deviation;
                this.newValue = newValue;
                this.previousValue = previousValue;
                this.timestamp = timestamp;
            }
            
            public String getDataKey() { return dataKey; }
            public BigDecimal getDeviation() { return deviation; }
            public BigDecimal getNewValue() { return newValue; }
            public BigDecimal getPreviousValue() { return previousValue; }
            public long getTimestamp() { return timestamp; }
        }
    }
}