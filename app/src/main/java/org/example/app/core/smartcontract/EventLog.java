
package org.example.app.core.smartcontract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Handles smart contract event logging and filtering
 */
public class EventLog {
    private static final Logger LOGGER = Logger.getLogger(EventLog.class.getName());
    
    // Main event storage - mapping of contract address to list of events
    private final Map<String, List<Event>> eventStorage = new HashMap<>();
    
    // Topic index - mapping of topics to events for efficient filtering
    private final Map<String, List<Event>> topicIndex = new HashMap<>();
    
    /**
     * Log a new event emitted by a contract
     * 
     * @param contractAddress Address of the contract that emitted the event
     * @param eventSignature Event signature (e.g., "Transfer(address,address,uint256)")
     * @param topics Array of indexed parameters (topics)
     * @param data Non-indexed parameters data
     * @param blockNumber Block number where the event was emitted
     * @param transactionHash Hash of the transaction that emitted the event
     * @param transactionIndex Index of the transaction in the block
     */
    public void logEvent(
            String contractAddress,
            String eventSignature,
            String[] topics,
            byte[] data,
            long blockNumber,
            String transactionHash,
            int transactionIndex) {
        
        LOGGER.info("Logging event: " + eventSignature + " from contract: " + contractAddress);
        
        // Create a new event
        Event event = new Event(
                contractAddress,
                eventSignature,
                topics,
                data,
                blockNumber,
                transactionHash,
                transactionIndex
        );
        
        // Add the event to the storage
        eventStorage.computeIfAbsent(contractAddress, k -> new ArrayList<>()).add(event);
        
        // Index the event by topics
        indexEvent(event);
        
        LOGGER.fine("Event logged successfully");
    }
    
    /**
     * Index an event by its topics for efficient filtering
     */
    private void indexEvent(Event event) {
        // Index by event signature (first topic)
        topicIndex.computeIfAbsent(event.getEventSignature(), k -> new ArrayList<>()).add(event);
        
        // Index by other topics
        for (String topic : event.getTopics()) {
            if (topic != null && !topic.isEmpty()) {
                topicIndex.computeIfAbsent(topic, k -> new ArrayList<>()).add(event);
            }
        }
    }
    
    /**
     * Get events from a specific contract
     */
    public List<Event> getEvents(String contractAddress) {
        return eventStorage.getOrDefault(contractAddress, new ArrayList<>());
    }
    
    /**
     * Filter events by contract and event signature
     */
    public List<Event> filterEvents(String contractAddress, String eventSignature) {
        List<Event> result = new ArrayList<>();
        
        // Get all events for the contract
        List<Event> contractEvents = getEvents(contractAddress);
        
        // Filter by event signature
        for (Event event : contractEvents) {
            if (event.getEventSignature().equals(eventSignature)) {
                result.add(event);
            }
        }
        
        return result;
    }
    
    /**
     * Filter events by contract, event signature, and topic
     */
    public List<Event> filterEvents(String contractAddress, String eventSignature, String topic) {
        List<Event> result = new ArrayList<>();
        
        // First filter by contract and event signature
        List<Event> filteredBySignature = filterEvents(contractAddress, eventSignature);
        
        // Then filter by topic
        for (Event event : filteredBySignature) {
            for (String eventTopic : event.getTopics()) {
                if (topic.equals(eventTopic)) {
                    result.add(event);
                    break;
                }
            }
        }
        
        return result;
    }
    
    /**
     * Clear all events (for testing purposes)
     */
    public void clearEvents() {
        eventStorage.clear();
        topicIndex.clear();
    }
    
    /**
     * Class to represent a contract event
     */
    public static class Event {
        private final String contractAddress;
        private final String eventSignature;
        private final String[] topics;
        private final byte[] data;
        private final long blockNumber;
        private final String transactionHash;
        private final int transactionIndex;
        
        public Event(
                String contractAddress,
                String eventSignature,
                String[] topics,
                byte[] data,
                long blockNumber,
                String transactionHash,
                int transactionIndex) {
            
            this.contractAddress = contractAddress;
            this.eventSignature = eventSignature;
            this.topics = topics;
            this.data = data;
            this.blockNumber = blockNumber;
            this.transactionHash = transactionHash;
            this.transactionIndex = transactionIndex;
        }
        
        public String getContractAddress() {
            return contractAddress;
        }
        
        public String getEventSignature() {
            return eventSignature;
        }
        
        public String[] getTopics() {
            return topics;
        }
        
        public byte[] getData() {
            return data;
        }
        
        public long getBlockNumber() {
            return blockNumber;
        }
        
        public String getTransactionHash() {
            return transactionHash;
        }
        
        public int getTransactionIndex() {
            return transactionIndex;
        }
    }
}