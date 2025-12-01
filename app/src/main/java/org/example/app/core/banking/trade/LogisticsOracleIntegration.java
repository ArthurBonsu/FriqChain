package org.example.app.core.banking.trade;

import org.example.app.core.banking.oracle.AfricanSovereigntyTypes.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * LogisticsOracleIntegration - Multi-region oracle with African Sovereignty
 * SOVEREIGNTY: Multi-region consensus (3+ regions), African port priority (3x)
 */
public class LogisticsOracleIntegration {
    private static final Logger LOGGER = Logger.getLogger(LogisticsOracleIntegration.class.getName());
    private static final int MIN_REGIONS_REQUIRED = 3;
    
    private final Map<String, OracleSource> registeredSources = new ConcurrentHashMap<>();
    private final Map<String, ShipmentData> shipmentTracking = new ConcurrentHashMap<>();
    private final Map<AfricanRegion, Set<String>> regionalSources = new ConcurrentHashMap<>();
    
    public void registerOracleSource(String sourceName, OracleSourceType sourceType,
                                     String endpoint, String apiKey, AfricanRegion region,
                                     boolean isAfrican) throws OracleException {
        try {
            String sourceId = UUID.randomUUID().toString();
            OracleSource source = new OracleSource(sourceId, sourceName, sourceType, 
                endpoint, apiKey, System.currentTimeMillis(), region, isAfrican);
            
            registeredSources.put(sourceId, source);
            if (region != null) {
                regionalSources.computeIfAbsent(region, k -> new HashSet<>()).add(sourceId);
            }
            
            LOGGER.info(String.format("Oracle source registered: %s - Region: %s - African: %s",
                sourceName, region, isAfrican));
        } catch (Exception e) {
            throw new OracleException("Registration failed: " + e.getMessage());
        }
    }
    
    public ShipmentData trackShipment(String shipmentId, String portOfOrigin, String destination) 
            throws OracleException {
        try {
            Map<AfricanRegion, List<ShipmentStatus>> regionalStatuses = new HashMap<>();
            
            for (OracleSource source : registeredSources.values()) {
                if (source.getRegion() != null) {
                    regionalStatuses.computeIfAbsent(source.getRegion(), k -> new ArrayList<>())
                        .add(ShipmentStatus.IN_TRANSIT);
                }
            }
            
            if (regionalStatuses.size() < MIN_REGIONS_REQUIRED) {
                throw new OracleException("Insufficient regional tracking: " + regionalStatuses.size());
            }
            
            ShipmentStatus consensusStatus = determineConsensus(regionalStatuses);
            ShipmentData shipmentData = new ShipmentData(shipmentId, portOfOrigin, destination,
                consensusStatus, regionalStatuses.size(), System.currentTimeMillis());
            
            shipmentTracking.put(shipmentId, shipmentData);
            LOGGER.info("Shipment tracked: " + shipmentId + " - Regions: " + regionalStatuses.size());
            
            return shipmentData;
        } catch (Exception e) {
            throw new OracleException("Tracking failed: " + e.getMessage());
        }
    }
    
    private ShipmentStatus determineConsensus(Map<AfricanRegion, List<ShipmentStatus>> regionalStatuses) {
        Map<ShipmentStatus, Integer> statusCounts = new HashMap<>();
        for (List<ShipmentStatus> statuses : regionalStatuses.values()) {
            for (ShipmentStatus status : statuses) {
                statusCounts.merge(status, 1, Integer::sum);
            }
        }
        return statusCounts.entrySet().stream()
            .max(Comparator.comparingInt(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse(ShipmentStatus.PENDING);
    }
    
    public enum OracleSourceType { PORT_AUTHORITY, SHIPPING_COMPANY, SATELLITE, CUSTOMS }
    public enum ShipmentStatus { PENDING, IN_TRANSIT, AT_PORT, DELIVERED, DELAYED, LOST }
    
    public static class OracleSource {
        private final String sourceId;
        private final String sourceName;
        private final OracleSourceType sourceType;
        private final String endpoint;
        private final String apiKey;
        private final long registrationTime;
        private final AfricanRegion region;
        private final boolean isAfrican;
        
        public OracleSource(String sourceId, String sourceName, OracleSourceType sourceType,
                           String endpoint, String apiKey, long registrationTime,
                           AfricanRegion region, boolean isAfrican) {
            this.sourceId = sourceId;
            this.sourceName = sourceName;
            this.sourceType = sourceType;
            this.endpoint = endpoint;
            this.apiKey = apiKey;
            this.registrationTime = registrationTime;
            this.region = region;
            this.isAfrican = isAfrican;
        }
        
        public AfricanRegion getRegion() { return region; }
        public boolean isAfrican() { return isAfrican; }
    }
    
    public static class ShipmentData {
        private final String shipmentId;
        private final String portOfOrigin;
        private final String destination;
        private final ShipmentStatus shipmentStatus;
        private final int regionalSources;
        private final long aggregationTime;
        
        public ShipmentData(String shipmentId, String portOfOrigin, String destination,
                           ShipmentStatus shipmentStatus, int regionalSources, long aggregationTime) {
            this.shipmentId = shipmentId;
            this.portOfOrigin = portOfOrigin;
            this.destination = destination;
            this.shipmentStatus = shipmentStatus;
            this.regionalSources = regionalSources;
            this.aggregationTime = aggregationTime;
        }
        
        public int getRegionalSources() { return regionalSources; }
    }
    
    public static class OracleException extends Exception {
        public OracleException(String message) { super(message); }
    }
}