package tech.kayys.cifr.engine.tool;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.patch.AppliedPatch;
import tech.kayys.cifr.engine.patch.PatchApplier;
import tech.kayys.cifr.engine.patch.PatchEdit;
import tech.kayys.cifr.engine.patch.PatchOperation;
import tech.kayys.cifr.engine.repo.GitRepositoryService;
import tech.kayys.cifr.engine.repo.RepositorySnapshot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class WorkspacePatchTool implements CifrTool {

    @Inject
    PatchApplier applier;

    @Inject
    GitRepositoryService gitService;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "workspace_patch",
                "Applies code patches to workspace files using expected and replacement texts.",
                ToolCategory.FILESYSTEM,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string", "description", "Workspace-relative file path"),
                                "edits", Map.of(
                                        "type", "array",
                                        "items", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "expectedText", Map.of("type", "string"),
                                                        "replacementText", Map.of("type", "string")
                                                ),
                                                "required", List.of("expectedText", "replacementText")
                                        )
                                )
                        ),
                        "required", List.of("path", "edits")
                ),
                Set.of(ToolCapability.WRITE, ToolCapability.MUTATES_WORKSPACE),
                ToolRisk.HIGH
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                Map<String, Object> args = request.arguments();
                String path = (String) args.get("path");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> editArgs = (List<Map<String, Object>>) args.get("edits");
                if (path == null || path.isBlank() || editArgs == null || editArgs.isEmpty()) {
                    return new WorkspacePatchResult(false, "A workspace-relative path and at least one edit are required");
                }

                List<PatchEdit> edits = new ArrayList<>();
                for (Map<String, Object> edit : editArgs) {
                    String expected = (String) edit.get("expectedText");
                    String replacement = (String) edit.get("replacementText");
                    if (expected == null || replacement == null) {
                        return new WorkspacePatchResult(false, "Each edit requires expectedText and replacementText");
                    }
                    edits.add(new PatchEdit(expected, replacement));
                }

                Path workspaceRoot = context.resolveWorkspacePath(".");
                RepositorySnapshot baseline = null;
                try {
                    baseline = gitService != null ? gitService.captureSnapshot(workspaceRoot) : null;
                } catch (Exception ignored) {
                    // Patch validation and path containment still apply without a Git baseline.
                }

                PatchOperation patch = new PatchOperation(
                        UUID.randomUUID().toString(),
                        path,
                        edits,
                        "Applied by CIFR workspace patch tool",
                        null,
                        baseline
                );
                AppliedPatch result = applier.apply(patch, context);
                return new WorkspacePatchResult(true,
                        "Patch applied: " + path + " (" + edits.size() + " edit(s), id=" + result.patchId() + ")");
            } catch (Exception e) {
                return new WorkspacePatchResult(false,
                        "Patch failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        });
    }

    private record WorkspacePatchResult(boolean success, String summary) implements ToolResult {
        @Override public boolean isSuccess() { return success; }
        @Override public String getSummary() { return summary; }
    }
}
