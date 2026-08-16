package tech.kayys.aljabr.engine.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.api.agent.AgentEvent;
import tech.kayys.aljabr.api.agent.AgentPhase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultEventLedgerTest {

    private String originalUserDir = System.getProperty("user.dir");
    private Path tempDir;

    @AfterEach
    public void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            // delete ledger directory recursively
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        }
        System.setProperty("user.dir", originalUserDir);
    }

    @Test
    public void recordsAndNotifiesListeners() throws Exception {
        tempDir = Files.createTempDirectory("aljabr-test-");
        System.setProperty("user.dir", tempDir.toString());

        DefaultEventLedger ledger = new DefaultEventLedger();
        ledger.init();

        AtomicReference<AgentEvent> seen = new AtomicReference<>();
        ledger.registerListener(seen::set);

        AgentEvent.PhaseStarted e = new AgentEvent.PhaseStarted("exec-1", AgentPhase.INITIALIZING, Instant.now());
        ledger.record(e);

        // listener should have been called
        assertNotNull(seen.get(), "Listener should receive event");
        assertTrue(seen.get() instanceof AgentEvent.PhaseStarted);

        // events file should exist and contain JSON
        Path ledgerDir = tempDir.resolve(".aljabr").resolve("ledger");
        assertTrue(Files.exists(ledgerDir), "Ledger directory should exist");
        Path eventsJson = ledgerDir.resolve("events.json");
        assertTrue(Files.exists(eventsJson), "events.json should be written");
        String content = Files.readString(eventsJson);
        // Jackson serializes the record fields; assert on execution id or phase instead of type name
        assertTrue(content.contains("exec-1") || content.contains("INITIALIZING"), "events.json should contain serialized event details");

        ledger.unregisterListener(seen::set);
    }
}
