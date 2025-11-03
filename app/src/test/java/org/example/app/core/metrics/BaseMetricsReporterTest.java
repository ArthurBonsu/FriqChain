package org.example.app.core.metrics;

import com.google.gson.Gson;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaseMetricsReporterTest {

    private NetworkMetrics mockNetworkMetrics;
    private ConsensusMetrics mockConsensusMetrics;
    private MetricsReporter reporter;
    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        mockNetworkMetrics = mock(NetworkMetrics.class);
        mockConsensusMetrics = mock(ConsensusMetrics.class);

        // Mock values
        when(mockNetworkMetrics.getTotalPeers()).thenReturn(5L);
        when(mockNetworkMetrics.getMessagesReceived()).thenReturn(100L);
        when(mockNetworkMetrics.getMessagesSent()).thenReturn(80L);

        when(mockConsensusMetrics.getBlockHeight()).thenReturn(200L);
        when(mockConsensusMetrics.getTotalTransactions()).thenReturn(500L);
        when(mockConsensusMetrics.getAverageBlockTime()).thenReturn(10L);

        tempFile = Files.createTempFile("metrics_test", ".json");
        reporter = new MetricsReporter(mockNetworkMetrics, mockConsensusMetrics, tempFile.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        reporter.stop();
        Files.deleteIfExists(tempFile);
    }

    @Test
    void testReportMetricsWritesToFile() throws Exception {
        reporter.start(1, TimeUnit.SECONDS);
        Thread.sleep(1500); // Let it run once

        reporter.stop();
        Thread.sleep(500); // Give some time to shutdown

        List<String> lines = Files.readAllLines(tempFile);
        assertFalse(lines.isEmpty(), "Metrics file should not be empty");

        // Validate JSON structure
        String jsonLine = lines.get(0);
        Gson gson = new Gson();
        assertDoesNotThrow(() -> {
            gson.fromJson(jsonLine, Object.class);
        }, "Output should be valid JSON");

        assertTrue(jsonLine.contains("totalPeers"), "Should contain totalPeers");
        assertTrue(jsonLine.contains("blockHeight"), "Should contain blockHeight");
    }

    @Test
    void testStopShutsDownScheduler() {
        reporter.start(1, TimeUnit.SECONDS);
        reporter.stop();
        assertTrue(true, "Scheduler stopped without exceptions");
    }
}
