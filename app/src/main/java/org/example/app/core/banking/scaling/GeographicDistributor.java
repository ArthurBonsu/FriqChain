package org.example.app.core.scaling;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * GeographicDistributor ensures proper geographic distribution across the TINC network.
 * 
 * Requirements:
 * - No single country controls >33% of nodes
 * - Balanced distribution across 5 African regions
 * - Minimum representation from each region
 * - Geographic diversity for fault tolerance
 */
public class GeographicDistributor {
    private static final Logger logger = Logger.getLogger(GeographicDistributor.class.getName());
    
    private static final double MAX_COUNTRY_PERCENTAGE = 0.33;
    private static final double MIN_REGION_PERCENTAGE = 0.10; // Each region should have at least 10%
    
    // Country-to-region mapping
    private final ConcurrentHashMap<String, String> countryToRegion;
    
    // Distribution tracking
    private final ConcurrentHashMap<String, Integer> countryNodeCount;
    private final ConcurrentHashMap<String, Integer> regionNodeCount;
    private int totalNodes;
    
    public GeographicDistributor() {
        this.countryToRegion = new ConcurrentHashMap<>();
        this.countryNodeCount = new ConcurrentHashMap<>();
        this.regionNodeCount = new ConcurrentHashMap<>();
        this.totalNodes = 0;
        
        initializeGeographicMappings();
    }
    
    /**
     * Initialize country-to-region mappings for all African countries
     */
    private void initializeGeographicMappings() {
        // North Africa
        mapCountryToRegion("Morocco", "NORTH_AFRICA");
        mapCountryToRegion("Egypt", "NORTH_AFRICA");
        mapCountryToRegion("Algeria", "NORTH_AFRICA");
        mapCountryToRegion("Tunisia", "NORTH_AFRICA");
        mapCountryToRegion("Libya", "NORTH_AFRICA");
        mapCountryToRegion("Sudan", "NORTH_AFRICA");
        mapCountryToRegion("Western Sahara", "NORTH_AFRICA");
        
        // West Africa
        mapCountryToRegion("Nigeria", "WEST_AFRICA");
        mapCountryToRegion("Ghana", "WEST_AFRICA");
        mapCountryToRegion("Senegal", "WEST_AFRICA");
        mapCountryToRegion("Côte d'Ivoire", "WEST_AFRICA");
        mapCountryToRegion("Mali", "WEST_AFRICA");
        mapCountryToRegion("Burkina Faso", "WEST_AFRICA");
        mapCountryToRegion("Niger", "WEST_AFRICA");
        mapCountryToRegion("Guinea", "WEST_AFRICA");
        mapCountryToRegion("Benin", "WEST_AFRICA");
        mapCountryToRegion("Togo", "WEST_AFRICA");
        mapCountryToRegion("Sierra Leone", "WEST_AFRICA");
        mapCountryToRegion("Liberia", "WEST_AFRICA");
        mapCountryToRegion("Mauritania", "WEST_AFRICA");
        mapCountryToRegion("Gambia", "WEST_AFRICA");
        mapCountryToRegion("Guinea-Bissau", "WEST_AFRICA");
        mapCountryToRegion("Cape Verde", "WEST_AFRICA");
        
        // East Africa
        mapCountryToRegion("Kenya", "EAST_AFRICA");
        mapCountryToRegion("Ethiopia", "EAST_AFRICA");
        mapCountryToRegion("Tanzania", "EAST_AFRICA");
        mapCountryToRegion("Uganda", "EAST_AFRICA");
        mapCountryToRegion("Rwanda", "EAST_AFRICA");
        mapCountryToRegion("Burundi", "EAST_AFRICA");
        mapCountryToRegion("Somalia", "EAST_AFRICA");
        mapCountryToRegion("Djibouti", "EAST_AFRICA");
        mapCountryToRegion("Eritrea", "EAST_AFRICA");
        mapCountryToRegion("South Sudan", "EAST_AFRICA");
        
        // Southern Africa
        mapCountryToRegion("South Africa", "SOUTHERN_AFRICA");
        mapCountryToRegion("Zimbabwe", "SOUTHERN_AFRICA");
        mapCountryToRegion("Botswana", "SOUTHERN_AFRICA");
        mapCountryToRegion("Namibia", "SOUTHERN_AFRICA");
        mapCountryToRegion("Zambia", "SOUTHERN_AFRICA");
        mapCountryToRegion("Mozambique", "SOUTHERN_AFRICA");
        mapCountryToRegion("Angola", "SOUTHERN_AFRICA");
        mapCountryToRegion("Malawi", "SOUTHERN_AFRICA");
        mapCountryToRegion("Lesotho", "SOUTHERN_AFRICA");
        mapCountryToRegion("Eswatini", "SOUTHERN_AFRICA");
        mapCountryToRegion("Madagascar", "SOUTHERN_AFRICA");
        mapCountryToRegion("Mauritius", "SOUTHERN_AFRICA");
        mapCountryToRegion("Seychelles", "SOUTHERN_AFRICA");
        mapCountryToRegion("Comoros", "SOUTHERN_AFRICA");
        
        // Central Africa
        mapCountryToRegion("Cameroon", "CENTRAL_AFRICA");
        mapCountryToRegion("DRC", "CENTRAL_AFRICA");
        mapCountryToRegion("Gabon", "CENTRAL_AFRICA");
        mapCountryToRegion("Congo", "CENTRAL_AFRICA");
        mapCountryToRegion("CAR", "CENTRAL_AFRICA");
        mapCountryToRegion("Chad", "CENTRAL_AFRICA");
        mapCountryToRegion("Equatorial Guinea", "CENTRAL_AFRICA");
        mapCountryToRegion("São Tomé and Príncipe", "CENTRAL_AFRICA");
        
        logger.info("Initialized geographic mappings for 54 African countries across 5 regions");
    }
    
    /**
     * Map country to region
     */
    private void mapCountryToRegion(String country, String region) {
        countryToRegion.put(country, region);
    }
    
    /**
     * Register a node and update distribution
     */
    public synchronized boolean registerNode(String country) {
        if (!countryToRegion.containsKey(country)) {
            logger.warning("Unknown country: " + country);
            return false;
        }
        
        // Check if adding this node would violate country limit
        int currentCountryCount = countryNodeCount.getOrDefault(country, 0);
        double newCountryPercentage = (double) (currentCountryCount + 1) / (totalNodes + 1);
        
        if (newCountryPercentage > MAX_COUNTRY_PERCENTAGE) {
            logger.warning("Cannot register node from " + country + " - would exceed 33% country limit");
            return false;
        }
        
        // Update counts
        countryNodeCount.merge(country, 1, Integer::sum);
        
        String region = countryToRegion.get(country);
        regionNodeCount.merge(region, 1, Integer::sum);
        
        totalNodes++;
        
        logger.info("Registered node from " + country + " (" + region + ") - Total nodes: " + totalNodes);
        return true;
    }
    
    /**
     * Validate shard distribution
     */
    public boolean validateShardDistribution(int shardId, String address) {
        // Extract country from address metadata (this would be implemented based on your addressing scheme)
        // For now, this is a placeholder
        return true;
    }
    
    /**
     * Get geographic distribution report
     */
    public GeographicReport getDistributionReport() {
        Map<String, Double> countryPercentages = new HashMap<>();
        Map<String, Double> regionPercentages = new HashMap<>();
        
        // Calculate percentages
        for (Map.Entry<String, Integer> entry : countryNodeCount.entrySet()) {
            double percentage = (double) entry.getValue() / totalNodes;
            countryPercentages.put(entry.getKey(), percentage);
        }
        
        for (Map.Entry<String, Integer> entry : regionNodeCount.entrySet()) {
            double percentage = (double) entry.getValue() / totalNodes;
            regionPercentages.put(entry.getKey(), percentage);
        }
        
        return new GeographicReport(totalNodes, countryPercentages, regionPercentages);
    }
    
    /**
     * Check if distribution meets minimum requirements
     */
    public boolean validateMinimumDistribution() {
        // Each region should have at least 10% representation
        for (String region : Arrays.asList("NORTH_AFRICA", "WEST_AFRICA", "EAST_AFRICA", 
                                           "SOUTHERN_AFRICA", "CENTRAL_AFRICA")) {
            int regionCount = regionNodeCount.getOrDefault(region, 0);
            double percentage = totalNodes > 0 ? (double) regionCount / totalNodes : 0.0;
            
            if (percentage < MIN_REGION_PERCENTAGE) {
                logger.warning("Region " + region + " only has " + 
                    String.format("%.2f%%", percentage * 100) + " (minimum 10% required)");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get recommended countries for next node
     */
    public List<String> getRecommendedCountries() {
        List<String> recommended = new ArrayList<>();
        
        // Find underrepresented regions
        String minRegion = null;
        int minCount = Integer.MAX_VALUE;
        
        for (String region : Arrays.asList("NORTH_AFRICA", "WEST_AFRICA", "EAST_AFRICA", 
                                           "SOUTHERN_AFRICA", "CENTRAL_AFRICA")) {
            int count = regionNodeCount.getOrDefault(region, 0);
            if (count < minCount) {
                minCount = count;
                minRegion = region;
            }
        }
        
        // Get countries from most underrepresented region
        if (minRegion != null) {
            final String targetRegion = minRegion;
            countryToRegion.forEach((country, region) -> {
                if (region.equals(targetRegion)) {
                    // Check if country hasn't exceeded limits
                    int countryCount = countryNodeCount.getOrDefault(country, 0);
                    double percentage = totalNodes > 0 ? (double) countryCount / totalNodes : 0.0;
                    
                    if (percentage < MAX_COUNTRY_PERCENTAGE) {
                        recommended.add(country);
                    }
                }
            });
        }
        
        return recommended;
    }
    
    /**
     * Get country for given country name
     */
    public String getRegion(String country) {
        return countryToRegion.get(country);
    }
    
    /**
     * Get all countries in a region
     */
    public List<String> getCountriesInRegion(String region) {
        List<String> countries = new ArrayList<>();
        countryToRegion.forEach((country, r) -> {
            if (r.equals(region)) {
                countries.add(country);
            }
        });
        return countries;
    }
    
    /**
     * Geographic distribution report
     */
    public static class GeographicReport {
        private final int totalNodes;
        private final Map<String, Double> countryPercentages;
        private final Map<String, Double> regionPercentages;
        
        public GeographicReport(int totalNodes, Map<String, Double> countryPercentages, 
                              Map<String, Double> regionPercentages) {
            this.totalNodes = totalNodes;
            this.countryPercentages = countryPercentages;
            this.regionPercentages = regionPercentages;
        }
        
        public int getTotalNodes() { return totalNodes; }
        public Map<String, Double> getCountryPercentages() { return new HashMap<>(countryPercentages); }
        public Map<String, Double> getRegionPercentages() { return new HashMap<>(regionPercentages); }
        
        public double getMaxCountryPercentage() {
            return countryPercentages.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        }
        
        public double getMinRegionPercentage() {
            return regionPercentages.values().stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(0.0);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Geographic Distribution Report:\n");
            sb.append("  Total Nodes: ").append(totalNodes).append("\n");
            sb.append("\n  Regional Distribution:\n");
            
            regionPercentages.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(entry -> {
                    sb.append(String.format("    %-20s: %5.2f%%\n", 
                        entry.getKey(), entry.getValue() * 100));
                });
            
            sb.append("\n  Top Countries:\n");
            countryPercentages.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    sb.append(String.format("    %-20s: %5.2f%%\n", 
                        entry.getKey(), entry.getValue() * 100));
                });
            
            return sb.toString();
        }
    }
}
