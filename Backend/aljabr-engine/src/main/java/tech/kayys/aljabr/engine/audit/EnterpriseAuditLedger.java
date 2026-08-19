package tech.kayys.aljabr.engine.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable, Cryptographically Chained Audit Ledger.
 * Provides compliance-ready (SOC 2, ISO 27001) audit trails for every agent decision,
 * tool execution, file modification, and human approval.
 */
@ApplicationScoped
public class EnterpriseAuditLedger {

    private static final Logger LOG = Logger.getLogger(EnterpriseAuditLedger.class);
    private static final Path AUDIT_DIR = Paths.get(System.getProperty("user.dir"), ".aljabr", "audit");
    private static final Path AUDIT_FILE = AUDIT_DIR.resolve("audit_trail.jsonl");

    public record AuditEntry(
        String id,
        long sequenceNumber,
        Instant timestamp,
        String traceId,
        String actor,
        String action,
        String resource,
        String status,
        long durationMs,
        String prevHash,
        String currentHash,
        Map<String, Object> metadata
    ) {}

    private final ObjectMapper mapper;
    private final AtomicLong sequenceGenerator = new AtomicLong(0);
    private volatile String lastHash = "0000000000000000000000000000000000000000000000000000000000000000";
    private final List<AuditEntry> recentEntries = Collections.synchronizedList(new ArrayList<>());

    public EnterpriseAuditLedger() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(AUDIT_DIR)) {
                Files.createDirectories(AUDIT_DIR);
            }
            if (Files.exists(AUDIT_FILE)) {
                List<String> lines = Files.readAllLines(AUDIT_FILE);
                for (String line : lines) {
                    if (line.isBlank()) continue;
                    try {
                        AuditEntry e = mapper.readValue(line, AuditEntry.class);
                        this.lastHash = e.currentHash();
                        this.sequenceGenerator.set(Math.max(this.sequenceGenerator.get(), e.sequenceNumber()));
                        this.recentEntries.add(e);
                    } catch (Exception ignored) {}
                }
            }
            LOG.infof("Enterprise Audit Ledger initialized at: %s (sequence: %d)", AUDIT_FILE, sequenceGenerator.get());
        } catch (IOException e) {
            LOG.errorf("Failed to initialize audit directory: %s", e.getMessage());
        }
    }

    /**
     * Records an action into the cryptographic audit chain.
     */
    public synchronized AuditEntry log(
        String traceId,
        String actor,
        String action,
        String resource,
        String status,
        long durationMs,
        Map<String, Object> metadata
    ) {
        long seq = sequenceGenerator.incrementAndGet();
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String prev = this.lastHash;

        String rawContent = String.format("%d|%s|%d|%s|%s|%s|%s|%d|%s",
            seq, id, now.toEpochMilli(), traceId, actor, action, resource, durationMs, prev);
        String currentHash = computeSha256(rawContent);
        this.lastHash = currentHash;

        AuditEntry entry = new AuditEntry(
            id, seq, now, traceId, actor, action, resource, status, durationMs, prev, currentHash, metadata
        );

        recentEntries.add(entry);
        if (recentEntries.size() > 500) {
            recentEntries.remove(0);
        }

        persistEntry(entry);
        return entry;
    }

    /**
     * Verifies the cryptographic chain integrity of the ledger.
     */
    public boolean verifyLedgerIntegrity() {
        if (!Files.exists(AUDIT_FILE)) return true;
        try {
            List<String> lines = Files.readAllLines(AUDIT_FILE);
            String expectedPrev = "0000000000000000000000000000000000000000000000000000000000000000";

            for (String line : lines) {
                if (line.isBlank()) continue;
                AuditEntry e = mapper.readValue(line, AuditEntry.class);
                if (!e.prevHash().equals(expectedPrev)) {
                    LOG.errorf("Audit chain tamper detected at sequence %d! Expected prev %s but found %s",
                        e.sequenceNumber(), expectedPrev, e.prevHash());
                    return false;
                }
                String rawContent = String.format("%d|%s|%d|%s|%s|%s|%s|%d|%s",
                    e.sequenceNumber(), e.id(), e.timestamp().toEpochMilli(), e.traceId(), e.actor(), e.action(), e.resource(), e.durationMs(), e.prevHash());
                String recalculated = computeSha256(rawContent);
                if (!recalculated.equals(e.currentHash())) {
                    LOG.errorf("Hash mismatch at sequence %d! Computed %s vs stored %s",
                        e.sequenceNumber(), recalculated, e.currentHash());
                    return false;
                }
                expectedPrev = e.currentHash();
            }
            return true;
        } catch (Exception e) {
            LOG.errorf("Failed to verify audit ledger: %s", e.getMessage());
            return false;
        }
    }

    public List<AuditEntry> getRecentEntries() {
        return new ArrayList<>(recentEntries);
    }

    private void persistEntry(AuditEntry entry) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(AUDIT_FILE.toFile(), true))) {
            writer.write(mapper.writeValueAsString(entry));
            writer.newLine();
        } catch (IOException e) {
            LOG.warnf("Failed to append to audit ledger: %s", e.getMessage());
        }
    }

    private String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
