package tech.kayys.cifr.engine.patch.mutation;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.cifr.engine.index.WorkspaceFingerprint;
import tech.kayys.cifr.engine.repo.ChangeRecord;
import tech.kayys.cifr.engine.repo.ChangeSet;
import tech.kayys.cifr.engine.repo.ChangeType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DefaultMutationEngine implements MutationEngine {

    private static final Logger LOG = Logger.getLogger(DefaultMutationEngine.class);

    private record RollbackEntry(Path workspaceRoot, Map<Path, String> backups) {}
    private final Map<String, RollbackEntry> rollbackJournal = new ConcurrentHashMap<>();

    @Override
    public Uni<MutationResult> apply(EditProposal proposal, Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            if (proposal == null || proposal.edits().isEmpty()) {
                throw new IllegalArgumentException("Proposal has no file edits to apply");
            }
            Path root = requireWorkspaceRoot(workspaceRoot);
            MutationId mutationId = MutationId.generate();
            Map<Path, String> backups = new HashMap<>();
            List<ChangeRecord> records = new ArrayList<>();
            List<String> evidence = new ArrayList<>(proposal.supportingEvidence());
            Map<Path, String> plannedNewContents = new HashMap<>();

            for (FileEdit fileEdit : proposal.edits()) {
                Path relativePath = fileEdit.file();
                Path absolutePath = resolveInside(root, relativePath);
                String currentContent = "";
                boolean fileExists = Files.exists(absolutePath, LinkOption.NOFOLLOW_LINKS);
                if (fileExists) {
                    try {
                        currentContent = Files.readString(absolutePath);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read target file: " + relativePath, e);
                    }
                }

                backups.put(relativePath, fileExists ? currentContent : null);
                String updatedContent = currentContent;
                for (TextEdit edit : fileEdit.edits()) {
                    if (edit.expectedText() != null && !edit.expectedText().isEmpty()) {
                        if (!updatedContent.contains(edit.expectedText())) {
                            throw new IllegalStateException(String.format(
                                    "Stale edit rejected: expected content not found in '%s'. Expected: [%s]",
                                    relativePath, edit.expectedText().trim()));
                        }
                        updatedContent = updatedContent.replace(edit.expectedText(), edit.replacementText());
                    } else if (!fileExists || updatedContent.isEmpty()) {
                        updatedContent = edit.replacementText();
                    }
                }
                plannedNewContents.put(relativePath, updatedContent);
            }

            try {
                for (Map.Entry<Path, String> entry : plannedNewContents.entrySet()) {
                    Path relPath = entry.getKey();
                    Path absPath = resolveInside(root, relPath);
                    String newText = entry.getValue();
                    if (absPath.getParent() != null) Files.createDirectories(absPath.getParent());

                    String orig = backups.get(relPath);
                    String beforeHash = orig != null ? String.valueOf(orig.hashCode()) : "0";
                    Files.writeString(absPath, newText);
                    String afterHash = String.valueOf(newText.hashCode());
                    ChangeType type = (orig == null) ? ChangeType.CREATED : ChangeType.MODIFIED;
                    records.add(new ChangeRecord(relPath.toString(), type, beforeHash, afterHash,
                            mutationId.value(), "mutation-step"));
                    evidence.add(relPath.toString());
                }
            } catch (IOException | RuntimeException e) {
                for (Map.Entry<Path, String> backup : backups.entrySet()) {
                    Path absPath = resolveInside(root, backup.getKey());
                    try {
                        if (backup.getValue() != null) Files.writeString(absPath, backup.getValue());
                        else Files.deleteIfExists(absPath);
                    } catch (IOException | RuntimeException ex) {
                        LOG.errorf("Error restoring file during write rollback: %s", backup.getKey());
                    }
                }
                throw new RuntimeException("Failed applying file edits, rolled back: " + e.getMessage(), e);
            }

            rollbackJournal.put(mutationId.value(), new RollbackEntry(root, Collections.unmodifiableMap(new HashMap<>(backups))));
            WorkspaceFingerprint fingerprint = new WorkspaceFingerprint(
                    "repo-hash-" + mutationId.value(), "1.0", "cfg-hash-" + mutationId.value());
            return new MutationResult(mutationId, new ChangeSet(records), evidence, fingerprint);
        });
    }

    @Override
    public Uni<Void> rollback(MutationId mutationId, Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            if (mutationId == null) throw new IllegalArgumentException("Mutation ID cannot be null");
            Path root = requireWorkspaceRoot(workspaceRoot);
            RollbackEntry rollback = rollbackJournal.get(mutationId.value());
            if (rollback == null) {
                throw new IllegalStateException("No rollback journal found for mutation ID: " + mutationId.value());
            }
            if (!rollback.workspaceRoot().equals(root)) {
                throw new SecurityException("Mutation belongs to a different workspace");
            }
            for (Map.Entry<Path, String> entry : rollback.backups().entrySet()) {
                Path absolutePath = resolveInside(root, entry.getKey());
                try {
                    if (entry.getValue() != null) Files.writeString(absolutePath, entry.getValue());
                    else Files.deleteIfExists(absolutePath);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to restore file: " + entry.getKey(), e);
                }
            }
            rollbackJournal.remove(mutationId.value(), rollback);
            return null;
        });
    }

    private Path requireWorkspaceRoot(Path workspaceRoot) {
        if (workspaceRoot == null) throw new IllegalStateException("An explicit workspace root is required");
        try {
            Path root = workspaceRoot.toRealPath();
            if (!Files.isDirectory(root)) throw new IllegalArgumentException("Workspace root is not a directory");
            return root;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot resolve workspace root", e);
        }
    }

    private Path resolveInside(Path root, Path relativePath) {
        if (relativePath == null || relativePath.isAbsolute()) {
            throw new SecurityException("Mutation paths must be workspace-relative");
        }
        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root)) throw new SecurityException("Mutation path escapes workspace: " + relativePath);
        try {
            Path existing = candidate;
            while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                existing = existing.getParent();
            }
            if (existing == null || !existing.toRealPath().startsWith(root)) {
                throw new SecurityException("Mutation path resolves outside workspace: " + relativePath);
            }
            if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) && !candidate.toRealPath().startsWith(root)) {
                throw new SecurityException("Mutation path resolves outside workspace: " + relativePath);
            }
            return candidate;
        } catch (IOException e) {
            throw new SecurityException("Cannot safely resolve mutation path: " + relativePath, e);
        }
    }
}
