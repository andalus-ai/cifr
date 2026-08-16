package tech.kayys.aljabr.engine.patch;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.logging.Logger;

@ApplicationScoped
public class MutationStore {

    private static final Logger log = Logger.getLogger(MutationStore.class.getName());
    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path STORE_DIR = WORKSPACE_ROOT.resolve(".aljabr").resolve("mutations");

    public boolean isApplied(String id) {
        try {
            return Files.exists(STORE_DIR.resolve(id + ".record"));
        } catch (Exception e) {
            log.warning("failed to check mutation store: " + e.getMessage());
            return false;
        }
    }

    public void recordApplied(String id) {
        recordApplied(id, null);
    }

    public void recordApplied(String id, tech.kayys.aljabr.engine.repo.ChangeSet changes) {
        recordApplied(id, changes, null);
    }

    public void recordApplied(String id, tech.kayys.aljabr.engine.repo.ChangeSet changes, tech.kayys.aljabr.engine.repo.RepositorySnapshot snapshot) {
        try {
            Files.createDirectories(STORE_DIR);
            StringBuilder sb = new StringBuilder();
            sb.append("id: ").append(id).append("\n");
            sb.append("appliedAt: ").append(Instant.now().toString()).append("\n");
            if (snapshot != null) {
                sb.append("baselineCommit: ").append(snapshot.commitHash()).append("\n");
                sb.append("branch: ").append(snapshot.branch()).append("\n");
                sb.append("dirty: ").append(snapshot.dirty()).append("\n");
                if (snapshot.changedFiles() != null && !snapshot.changedFiles().isEmpty()) {
                    sb.append("changedFiles:\n");
                    for (var f : snapshot.changedFiles()) sb.append(" - ").append(f).append("\n");
                }
            }

            if (changes != null && changes.changes() != null && !changes.changes().isEmpty()) {
                sb.append("changes:\n");
                for (var r : changes.changes()) {
                    sb.append("- path: ").append(r.path()).append(" before: ").append(r.beforeHash()).append(" after: ").append(r.afterHash()).append("\n");
                }
            } else {
                sb.append("changes: []\n");
            }
            Files.writeString(STORE_DIR.resolve(id + ".record"), sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to record mutation: " + e.getMessage(), e);
        }
    }

    /**
     * Lists applied mutation ids found in the mutations store.
     */
    public java.util.List<String> listApplied() {
        try {
            if (!Files.exists(STORE_DIR)) return java.util.List.of();
            try (var s = Files.list(STORE_DIR)) {
                return s
                        .filter(p -> p.getFileName().toString().endsWith(".record"))
                        .map(p -> p.getFileName().toString().replaceFirst("\\.record$", ""))
                        .sorted()
                        .toList();
            }
        } catch (IOException e) {
            log.warning("failed to list mutation store: " + e.getMessage());
            return java.util.List.of();
        }
    }

    /**
     * Read a mutation record's raw content.
     */
    public String readRecord(String id) {
        try {
            Path p = STORE_DIR.resolve(id + ".record");
            if (!Files.exists(p)) return null;
            return Files.readString(p);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read mutation record: " + e.getMessage(), e);
        }
    }
}
