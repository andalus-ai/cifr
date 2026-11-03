package tech.kayys.cifr.engine.patch;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.repo.ChangeRecord;
import tech.kayys.cifr.engine.repo.ChangeSet;
import tech.kayys.cifr.engine.repo.ChangeType;
import tech.kayys.cifr.engine.repo.GitRepositoryService;
import tech.kayys.cifr.engine.repo.RepositorySnapshot;
import tech.kayys.cifr.engine.tool.ToolExecutionContext;
import tech.kayys.andalus.extension.Id;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@ApplicationScoped
public class PatchApplier {

    @jakarta.inject.Inject
    PatchValidator validator;

    @jakarta.inject.Inject
    GitRepositoryService gitService;

    @jakarta.inject.Inject
    MutationStore mutationStore;

    @jakarta.inject.Inject
    WorkspaceGuard workspaceGuard;

    public AppliedPatch apply(PatchOperation patch, ToolExecutionContext context) {
        Path workspaceRoot = context.resolveWorkspacePath(".");
        if (mutationStore != null && mutationStore.isApplied(patch.id(), context)) {
            RepositorySnapshot snapshot = gitService != null ? gitService.captureSnapshot(workspaceRoot) : null;
            return new AppliedPatch(patch.id(), new ChangeSet(new ArrayList<>()), snapshot);
        }

        if (validator != null) {
            PatchValidationResult validation = validator.validate(patch, context);
            if (!validation.isValid()) {
                throw new IllegalArgumentException("Invalid patch: " + String.join(", ", validation.errors()));
            }
        }

        RepositorySnapshot baseline = null;
        if (patch.expectedSnapshot() != null) {
            baseline = patch.expectedSnapshot();
        } else if (patch.expectedCommit() != null && !patch.expectedCommit().isBlank()) {
            baseline = new RepositorySnapshot(patch.expectedCommit(), "unknown", false, List.of(), List.of());
        }

        if (workspaceGuard != null && baseline != null) {
            WorkspaceCheck check = workspaceGuard.check(baseline,
                    java.util.Set.of(Path.of(patch.path())), context);
            if (!check.ok()) {
                throw new IllegalStateException("Workspace conflict detected: " + String.join(",", check.conflicts()));
            }
        }

        Path target = context.resolveWorkspacePath(patch.path());
        List<ChangeRecord> records = new ArrayList<>();
        try {
            String currentContent = Files.readString(target);
            String beforeHash = computeSha256(currentContent);
            String newContent = currentContent;
            for (PatchEdit edit : patch.edits()) {
                newContent = newContent.replace(edit.expectedText(), edit.replacementText());
            }
            Files.writeString(target, newContent);
            String afterHash = computeSha256(newContent);

            records.add(new ChangeRecord(patch.path(), ChangeType.MODIFIED,
                    beforeHash, afterHash, patch.id(), Id.random().asString()));
            RepositorySnapshot appliedSnapshot = gitService != null
                    ? gitService.captureSnapshot(workspaceRoot) : null;
            if (mutationStore != null) {
                mutationStore.recordApplied(patch.id(), new ChangeSet(records), appliedSnapshot, context);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to apply patch to file: " + e.getMessage(), e);
        }

        RepositorySnapshot newSnapshot = gitService != null ? gitService.captureSnapshot(workspaceRoot) : null;
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
