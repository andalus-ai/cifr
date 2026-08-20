package tech.kayys.aljabr.engine.patch.mutation;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.index.WorkspaceFingerprint;
import tech.kayys.aljabr.engine.repo.ChangeRecord;
import tech.kayys.aljabr.engine.repo.ChangeSet;
import tech.kayys.aljabr.engine.repo.ChangeType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DefaultMutationEngine implements MutationEngine {

    private static final Logger LOG = Logger.getLogger(DefaultMutationEngine.class);

    // Rollback journal: mutationId -> (file relative path -> original content)
    private final Map<String, Map<Path, String>> rollbackJournal = new ConcurrentHashMap<>();

    @Override
    public Uni<MutationResult> apply(EditProposal proposal, Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            if (proposal == null || proposal.edits().isEmpty()) {
                throw new IllegalArgumentException("Proposal has no file edits to apply");
            }

            Path root = workspaceRoot != null ? workspaceRoot : Path.of(".");
            MutationId mutationId = MutationId.generate();
            Map<Path, String> backups = new HashMap<>();
            List<ChangeRecord> records = new ArrayList<>();
            List<String> evidence = new ArrayList<>(proposal.supportingEvidence());

            // 1. Validation & Pre-computation pass
            Map<Path, String> plannedNewContents = new HashMap<>();

            for (FileEdit fileEdit : proposal.edits()) {
                Path relativePath = fileEdit.file();
                Path absolutePath = root.resolve(relativePath).normalize();

                String currentContent = "";
                boolean fileExists = Files.exists(absolutePath);
                if (fileExists) {
                    try {
                        currentContent = Files.readString(absolutePath);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read target file: " + relativePath, e);
                    }
                }

                // Backup pre-mutation content
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
                        // Creating or rewriting file
                        updatedContent = edit.replacementText();
                    }
                }

                plannedNewContents.put(relativePath, updatedContent);
            }

            // 2. Atomic Application pass
            try {
                for (Map.Entry<Path, String> entry : plannedNewContents.entrySet()) {
                    Path relPath = entry.getKey();
                    Path absPath = root.resolve(relPath).normalize();
                    String newText = entry.getValue();

                    if (absPath.getParent() != null && !Files.exists(absPath.getParent())) {
                        Files.createDirectories(absPath.getParent());
                    }

                    String orig = backups.get(relPath);
                    String beforeHash = orig != null ? String.valueOf(orig.hashCode()) : "0";
                    Files.writeString(absPath, newText);
                    String afterHash = String.valueOf(newText.hashCode());

                    ChangeType type = (orig == null) ? ChangeType.CREATED : ChangeType.MODIFIED;
                    records.add(new ChangeRecord(relPath.toString(), type, beforeHash, afterHash, mutationId.value(), "mutation-step"));
                    evidence.add(relPath.toString());
                }
            } catch (IOException e) {
                // Rollback on write failure
                for (Map.Entry<Path, String> backup : backups.entrySet()) {
                    Path absPath = root.resolve(backup.getKey()).normalize();
                    try {
                        if (backup.getValue() != null) {
                            Files.writeString(absPath, backup.getValue());
                        } else {
                            Files.deleteIfExists(absPath);
                        }
                    } catch (IOException ex) {
                        LOG.errorf("Error restoring file during write rollback: %s", backup.getKey());
                    }
                }
                throw new RuntimeException("Failed applying file edits, rolled back: " + e.getMessage(), e);
            }

            // Store journal for later explicit rollback
            rollbackJournal.put(mutationId.value(), backups);

            WorkspaceFingerprint fingerprint = new WorkspaceFingerprint(
                "repo-hash-" + mutationId.value(),
                "1.0",
                "cfg-hash-" + mutationId.value()
            );

            return new MutationResult(
                mutationId,
                new ChangeSet(records),
                evidence,
                fingerprint
            );
        });
    }

    @Override
    public Uni<Void> rollback(MutationId mutationId, Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            if (mutationId == null) {
                throw new IllegalArgumentException("Mutation ID cannot be null");
            }

            Map<Path, String> backups = rollbackJournal.remove(mutationId.value());
            if (backups == null) {
                throw new IllegalStateException("No rollback journal found for mutation ID: " + mutationId.value());
            }

            Path root = workspaceRoot != null ? workspaceRoot : Path.of(".");
            for (Map.Entry<Path, String> entry : backups.entrySet()) {
                Path absPath = root.resolve(entry.getKey()).normalize();
                try {
                    if (entry.getValue() != null) {
                        Files.writeString(absPath, entry.getValue());
                    } else {
                        Files.deleteIfExists(absPath);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to restore file: " + entry.getKey(), e);
                }
            }
            return null;
        });
    }
}
