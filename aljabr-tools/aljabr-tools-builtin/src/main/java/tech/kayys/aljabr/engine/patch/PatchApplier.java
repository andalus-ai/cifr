package tech.kayys.aljabr.engine.patch;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.repo.ChangeRecord;
import tech.kayys.aljabr.engine.repo.ChangeSet;
import tech.kayys.aljabr.engine.repo.ChangeType;
import tech.kayys.aljabr.engine.repo.GitRepositoryService;
import tech.kayys.aljabr.engine.repo.RepositorySnapshot;
import tech.kayys.andalus.extension.Id;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class PatchApplier {

    private static final Logger log = Logger.getLogger(PatchApplier.class.getName());
    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));

    @jakarta.inject.Inject
    PatchValidator validator;

    @jakarta.inject.Inject
    GitRepositoryService gitService;

    @jakarta.inject.Inject
    MutationStore mutationStore;

    @jakarta.inject.Inject
    WorkspaceGuard workspaceGuard;

    public AppliedPatch apply(PatchOperation patch) {
        // Idempotency: if we've already applied this mutation, return a no-op AppliedPatch
        if (mutationStore != null && mutationStore.isApplied(patch.id())) {
            log.info("Patch " + patch.id() + " already applied; returning current snapshot.");
            RepositorySnapshot snapshot = gitService != null ? gitService.captureSnapshot(WORKSPACE_ROOT) : null;
            return new AppliedPatch(patch.id(), new ChangeSet(new ArrayList<>()), snapshot);
        }

        if (validator != null) {
            PatchValidationResult validation = validator.validate(patch);
            if (!validation.isValid()) {
                throw new IllegalArgumentException("Invalid patch: " + String.join(", ", validation.errors()));
            }
        }

        // Workspace freshness check: prefer an attached RepositorySnapshot (captured
        // during inspection) if present; fall back to expectedCommit string if set.
        RepositorySnapshot baseline = null;
        if (patch.expectedSnapshot() != null) {
            baseline = patch.expectedSnapshot();
        } else if (patch.expectedCommit() != null && !patch.expectedCommit().isBlank()) {
            baseline = new RepositorySnapshot(patch.expectedCommit(), "unknown", false, java.util.List.of(), java.util.List.of());
        }

        if (workspaceGuard != null && baseline != null) {
            java.util.Set<java.nio.file.Path> affected = java.util.Set.of(java.nio.file.Path.of(patch.path()));
            WorkspaceCheck wc = workspaceGuard.check(baseline, affected);
            if (!wc.ok()) {
                throw new IllegalStateException("Workspace conflict detected: " + String.join(",", wc.conflicts()));
            }
        }

        Path absoluteTargetPath = WORKSPACE_ROOT.resolve(patch.path()).normalize();
        List<ChangeRecord> records = new ArrayList<>();

        try {
            String currentContent = Files.readString(absoluteTargetPath);
            String beforeHash = computeSha256(currentContent);
            
            String newContent = currentContent;
            for (PatchEdit edit : patch.edits()) {
                newContent = newContent.replace(edit.expectedText(), edit.replacementText());
            }

            Files.writeString(absoluteTargetPath, newContent);
            String afterHash = computeSha256(newContent);

            ChangeRecord record = new ChangeRecord(
                patch.path(),
                ChangeType.MODIFIED,
                beforeHash,
                afterHash,
                patch.id(),
                Id.random().asString()
            );
            records.add(record);

            // record mutation for idempotency and evidence (include snapshot)
            RepositorySnapshot appliedSnapshot = gitService != null ? gitService.captureSnapshot(WORKSPACE_ROOT) : null;
            if (mutationStore != null) {
                mutationStore.recordApplied(patch.id(), new ChangeSet(records), appliedSnapshot);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to apply patch to file: " + e.getMessage(), e);
        }

        RepositorySnapshot newSnapshot = gitService != null ? gitService.captureSnapshot(WORKSPACE_ROOT) : null;
        return new AppliedPatch(patch.id(), new ChangeSet(records), newSnapshot);
    }

    private String computeSha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(content.hashCode());
        }
    }
}
