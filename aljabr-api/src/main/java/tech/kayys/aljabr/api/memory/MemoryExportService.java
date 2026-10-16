package tech.kayys.aljabr.api.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.andalus.memory.MemoryProvider;
import tech.kayys.andalus.memory.MemoryRecord;
import tech.kayys.andalus.memory.MemorySnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Orchestrates portable export/import/backup of all memory tiers.
 *
 * <p>Reads from all registered {@link MemoryProvider} implementations (short-term,
 * working, episodic, long-term) and produces a single self-describing
 * {@link MemorySnapshot} that can be serialized as JSON and restored on any
 * compatible Andalus runtime instance.
 */
@ApplicationScoped
public class MemoryExportService {

    private static final Logger LOG = Logger.getLogger(MemoryExportService.class);
    private static final String SNAPSHOT_FILE_PREFIX = "memory-snapshot";

    private final ObjectMapper mapper;

    @Inject
    Instance<MemoryProvider> providers;

    public MemoryExportService() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    // --- Export ---

    /**
     * Exports all memory records for the given agent across all registered providers
     * into a single portable {@link MemorySnapshot}.
     */
    public Uni<MemorySnapshot> exportSnapshot(String agentId, String userId, String tenantId) {
        return Uni.createFrom().item(() -> {
            List<MemoryRecord> allRecords = new ArrayList<>();
            List<String> tiers = new ArrayList<>();

            if (providers != null) {
                for (MemoryProvider provider : providers) {
                    String tier = provider.name();
                    try {
                        List<MemoryRecord> records = provider.exportAll(agentId);
                        allRecords.addAll(records);
                        tiers.add(tier);
                        LOG.infof("Exported %d records from tier '%s' for agent '%s'", records.size(), tier, agentId);
                    } catch (Exception e) {
                        LOG.warnf("Failed to export from tier '%s': %s", tier, e.getMessage());
                    }
                }
            }

            return MemorySnapshot.builder()
                    .snapshotId("snapshot-" + UUID.randomUUID())
                    .agentId(agentId)
                    .userId(userId)
                    .tenantId(tenantId)
                    .capturedAt(Instant.now())
                    .tiers(tiers)
                    .records(allRecords)
                    .exportMetadata(Map.of(
                            "totalRecords", allRecords.size(),
                            "tiers", tiers,
                            "exportedAt", Instant.now().toString()
                    ))
                    .build();
        });
    }

    /**
     * Serializes a {@link MemorySnapshot} to JSON bytes (pretty-printed).
     */
    public byte[] serializeSnapshot(MemorySnapshot snapshot) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(snapshot);
    }

    /**
     * Writes a snapshot to disk at the specified path.
     * If path is a directory, auto-names the file as {@code memory-snapshot-<agentId>-<timestamp>.json}.
     */
    public Path backupToDisk(MemorySnapshot snapshot, Path destination) throws IOException {
        Path targetFile = Files.isDirectory(destination)
                ? destination.resolve(SNAPSHOT_FILE_PREFIX + "-" + snapshot.agentId() + "-"
                        + snapshot.capturedAt().toEpochMilli() + ".json")
                : destination;

        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }
        Files.write(targetFile, serializeSnapshot(snapshot));
        LOG.infof("Memory snapshot written to: %s (%d records)", targetFile, snapshot.records().size());
        return targetFile;
    }

    // --- Import / Restore ---

    /**
     * Deserializes a {@link MemorySnapshot} from JSON bytes.
     */
    public MemorySnapshot deserializeSnapshot(byte[] json) throws IOException {
        return mapper.readValue(json, MemorySnapshot.class);
    }

    /**
     * Deserializes a {@link MemorySnapshot} from JSON string.
     */
    public MemorySnapshot deserializeSnapshot(String json) throws IOException {
        return deserializeSnapshot(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads a snapshot from disk.
     */
    public MemorySnapshot readFromDisk(Path file) throws IOException {
        return deserializeSnapshot(Files.readAllBytes(file));
    }

    /**
     * Imports a snapshot into all registered providers.
     * Records are routed to providers based on the {@code type} field in each {@link MemoryRecord}.
     *
     * @param snapshot  the snapshot to restore
     * @param overwrite if true, existing records with the same key are overwritten
     * @return an {@link ImportResult} with counts per tier
     */
    public Uni<ImportResult> importSnapshot(MemorySnapshot snapshot, boolean overwrite) {
        return Uni.createFrom().item(() -> {
            Map<String, Integer> countsByTier = new LinkedHashMap<>();
            int totalWritten = 0;
            int totalSkipped = 0;

            // Group records by type/tier
            Map<String, List<MemoryRecord>> byType = new LinkedHashMap<>();
            for (MemoryRecord record : snapshot.records()) {
                byType.computeIfAbsent(record.type() != null ? record.type() : "default", k -> new ArrayList<>())
                      .add(record);
            }

            if (providers != null) {
                for (MemoryProvider provider : providers) {
                    String tier = provider.name();
                    List<MemoryRecord> toWrite = byType.getOrDefault(tier,
                            byType.getOrDefault("default", List.of()));
                    if (toWrite.isEmpty()) continue;
                    try {
                        int written = provider.importAll(toWrite, overwrite);
                        int skipped = toWrite.size() - written;
                        countsByTier.put(tier, written);
                        totalWritten += written;
                        totalSkipped += skipped;
                        LOG.infof("Imported %d/%d records into tier '%s'", written, toWrite.size(), tier);
                    } catch (Exception e) {
                        LOG.warnf("Failed to import into tier '%s': %s", tier, e.getMessage());
                    }
                }
            }

            return new ImportResult(
                    snapshot.snapshotId(),
                    snapshot.agentId(),
                    snapshot.records().size(),
                    totalWritten,
                    totalSkipped,
                    countsByTier,
                    Instant.now()
            );
        });
    }

    // --- Result types ---

    public record ImportResult(
            String snapshotId,
            String agentId,
            int totalRecords,
            int writtenRecords,
            int skippedRecords,
            Map<String, Integer> countsByTier,
            Instant importedAt
    ) {}
}
