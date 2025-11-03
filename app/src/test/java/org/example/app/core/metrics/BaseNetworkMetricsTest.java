package org.example.app.core.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BaseNetworkMetricsTest {

    private NetworkMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new NetworkMetrics();
    }

    @Test
    void testRecordPeerConnectionIncrementsTotalPeers() {
        metrics.recordPeerConnection("peer1");
        metrics.recordPeerConnection("peer2");

        assertEquals(2, metrics.getTotalPeers());
        Map<String, Long> latencies = metrics.getPeerLatencies();
        assertTrue(latencies.containsKey("peer1"));
        assertTrue(latencies.containsKey("peer2"));
    }

    @Test
    void testRecordPeerDisconnectionDecrementsTotalPeers() {
        metrics.recordPeerConnection("peer1");
        metrics.recordPeerDisconnection("peer1");

        assertEquals(0, metrics.getTotalPeers());
        assertFalse(metrics.getPeerLatencies().containsKey("peer1"));
    }

    @Test
    void testRecordMessageReceived() {
        metrics.recordMessageReceived(100);
        metrics.recordMessageReceived(200);

        assertEquals(2, metrics.getMessagesReceived());
        assertEquals(300, metrics.getBytesReceived());
    }

    @Test
    void testRecordMessageSent() {
        metrics.recordMessageSent(50);
        metrics.recordMessageSent(75);

        assertEquals(2, metrics.getMessagesSent());
        assertEquals(125, metrics.getBytesSent());
    }

    @Test
    void testRecordPeerLatencyUpdatesValue() {
        String peerId = "peer123";
        metrics.recordPeerConnection(peerId);

        metrics.recordPeerLatency(peerId, 150);
        metrics.recordPeerLatency(peerId, 250);

        assertEquals(250L, metrics.getPeerLatencies().get(peerId));
    }

    @Test
    void testRecordPeerLatencyIgnoresUnknownPeer() {
        metrics.recordPeerLatency("unknownPeer", 123);
        assertFalse(metrics.getPeerLatencies().containsKey("unknownPeer"));
    }
}
