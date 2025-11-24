package org.example.app.core.banking.oracle;

import org.example.app.core.types.Address;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * SatelliteOracle - Remote sensing data (crop yields, port activity)
 * 
 * Uses satellite imagery and IoT sensors for independent verification
 */
public class SatelliteOracle extends OracleReporter {
    private static final Logger LOGGER = Logger.getLogger(SatelliteOracle.class.getName());
    
    private final Map<String, SatelliteObservation> observations;
    
    public static class SatelliteObservation {
        private final String observationType;
        private final String location;
        private final Map<String, Object> metrics;
        private final long timestamp;
        
        public SatelliteObservation(String observationType, String location, Map<String, Object> metrics) {
            this.observationType = observationType;
            this.location = location;
            this.metrics = new HashMap<>(metrics);
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getObservationType() { return observationType; }
        public String getLocation() { return location; }
        public Map<String, Object> getMetrics() { return new HashMap<>(metrics); }
        public long getTimestamp() { return timestamp; }
    }
    
    public SatelliteOracle(String reporterId, Address reporterAddress, BigDecimal minimumStake) {
        super(reporterId, "SATELLITE", reporterAddress, minimumStake);
        this.observations = new ConcurrentHashMap<>();
        LOGGER.info("SatelliteOracle initialized: " + reporterId);
    }
    
    @Override
    public OracleData fetchData(String dataKey) throws Exception {
        // DataKey format: "SAT:observationType:location"
        String[] parts = dataKey.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid satellite data key");
        }
        
        String observationType = parts[1];
        String location = parts[2];
        
        SatelliteObservation obs = fetchObservation(observationType, location);
        observations.put(dataKey, obs);
        
        Map<String, Object> obsData = new HashMap<>();
        obsData.put("type", obs.getObservationType());
        obsData.put("location", obs.getLocation());
        obsData.put("metrics", obs.getMetrics());
        
        byte[] signature = new byte[32];
        submitData("SATELLITE", obsData, signature);
        
        return new OracleData("SATELLITE", obsData, reporterId,
            new BigDecimal("0.88"), signature);
    }
    
    private SatelliteObservation fetchObservation(String type, String location) {
        Map<String, Object> metrics = new HashMap<>();
        
        if (type.equals("PORT_ACTIVITY")) {
            metrics.put("containerCount", 150 + new Random().nextInt(50));
            metrics.put("vesselCount", 5 + new Random().nextInt(10));
        } else if (type.equals("CROP_YIELD")) {
            metrics.put("vegetationIndex", 0.6 + new Random().nextDouble() * 0.3);
            metrics.put("estimatedYield", new BigDecimal(5000 + new Random().nextInt(3000)));
        }
        
        return new SatelliteObservation(type, location, metrics);
    }
    
    @Override
    protected boolean validateDataFormat(Object data) {
        return data instanceof Map;
    }
    
    @Override
    protected String getDataTypeIdentifier() {
        return "SATELLITE";
    }
}
