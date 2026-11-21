package tech.kayys.cifr.engine.patch;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.ToolExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class MutationStore {

    private static final Logger log = Logger.getLogger(MutationStore.class.getName());

    private Path storeDir(ToolExecutionContext context) {
        return context.resolveWorkspacePath(".cifr/mutations");
    }

    private Path recordPath(String id, ToolExecutionContext context) {
        if (id == null || !id.matches("[A-Za-z0-9._-]{1,128}") || id.equals(".") || id.equals("..")) {
            throw new IllegalArgumentException("Invalid mutation id");
        }
        return storeDir(context).resolve(id + ".record");
    }

    public boolean isApplied(String id, ToolExecutionContext context) {
        try {
            return Files.exists(recordPath(id, context));
        } catch (Exception e) {
            log.warning("failed to check mutation store: " + e.getMessage());
            throw new IllegalStateException("Unable to verify mutation idempotency", e);
        }
    }

    public void recordApplied(String id, ToolExecutionContext context) {
        recordApplied(id, null, null, context);
    }

    public void recordApplied(String id, tech.kayys.cifr.engine.repo.ChangeSet changes,
            ToolExecutionContext context) {
        recordApplied(id, changes, null, context);
    }

    public void recordApplied(String id, tech.kayys.cifr.engine.repo.ChangeSet changes,
            tech.kayys.cifr.engine.repo.RepositorySnapshot snapshot, ToolExecutionContext context) {
        try {
            Path dir = storeDir(context);
            Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder();
            sb.append("id: ").append(id).append("\n");
            sb.append("appliedAt: ").append(Instant.now()).append("\n");
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
                    sb.append("- path: ").append(r.path()).append(" before: ")
                            .append(r.beforeHash()).append(" after: ").append(r.afterHash()).append("\n");
                }
            } else {
                sb.append("changes: []\n");
            }
            Files.writeString(recordPath(id, context), sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to record mutation: " + e.getMessage(), e);
        }
    }

    public List<String> listApplied(ToolExecutionContext context) {
        try {
            Path dir = storeDir(context);
            if (!Files.exists(dir)) return List.of();
            try (var stream = Files.list(dir)) {
                return stream.filter(p -> p.getFileName().toString().endsWith(".record"))
                        .map(p -> p.getFileName().toString().replaceFirst("\\.record$", ""))
                        .sorted().toList();
            }
        } catch (IOException e) {
            log.warning("failed to list mutation store: " + e.getMessage());
            throw new IllegalStateException("Unable to list mutation records", e);
        }
    }

    public String readRecord(String id, ToolExecutionContext context) {
        try {
            Path path = recordPath(id, context);
            return Files.exists(path) ? Files.readString(path) : null;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read mutation record: " + e.getMessage(), e);
        }
    }
}
