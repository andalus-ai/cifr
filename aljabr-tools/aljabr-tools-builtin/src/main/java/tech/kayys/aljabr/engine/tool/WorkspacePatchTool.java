package tech.kayys.aljabr.engine.tool;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.patch.AppliedPatch;
import tech.kayys.aljabr.engine.patch.PatchApplier;
import tech.kayys.aljabr.engine.patch.PatchEdit;
import tech.kayys.aljabr.engine.patch.PatchOperation;
import tech.kayys.aljabr.engine.repo.GitRepositoryService;
import tech.kayys.aljabr.engine.repo.RepositorySnapshot;
import tech.kayys.andalus.descriptor.CapabilityDescriptor;
import tech.kayys.andalus.descriptor.ParameterDescriptor;
import tech.kayys.andalus.extension.Id;
import tech.kayys.andalus.extension.Metadata;
import tech.kayys.andalus.identity.ResourceId;
import tech.kayys.andalus.resource.ResourceType;
import tech.kayys.andalus.tool.Tool;
import tech.kayys.andalus.tool.ToolContext;
import tech.kayys.andalus.tool.ToolDescriptor;
import tech.kayys.andalus.tool.ToolInvocation;
import tech.kayys.andalus.tool.ToolResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class WorkspacePatchTool implements Tool {

    @Inject
    PatchApplier applier;

    @Inject
    GitRepositoryService gitService;

    private static final java.nio.file.Path WORKSPACE_ROOT = java.nio.file.Paths.get(System.getProperty("user.dir"));

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor() {
            @Override
            public String name() {
                return "workspace_patch";
            }

            @Override
            public String description() {
                return "Applies code patches to workspace files using expected and replacement texts.";
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string", "description", "Relative path to file"),
                                "edits", Map.of(
                                        "type", "array",
                                        "items", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "expectedText", Map.of("type", "string"),
                                                        "replacementText", Map.of("type", "string")
                                                )
                                        )
                                )
                        ),
                        "required", List.of("path", "edits")
                );
            }

            @Override
            public ResourceId id() {
                return ResourceId.from(Id.random(), ResourceType.fromString("tool"));
            }

            @Override
            public ResourceType type() {
                return ResourceType.fromString("tool");
            }

            @Override
            public Metadata metadata() {
                return Metadata.empty();
            }

            @Override
            public Set<String> tags() {
                return Collections.emptySet();
            }

            @Override
            public Set<String> categories() {
                return Collections.emptySet();
            }

            @Override
            public Map<String, ParameterDescriptor> inputs() {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, ParameterDescriptor> outputs() {
                return Collections.emptyMap();
            }

            @Override
            public List<CapabilityDescriptor> capabilities() {
                return Collections.emptyList();
            }
        };
    }

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, ToolContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> args = invocation.arguments();
                String path = (String) args.get("path");
                
                List<Map<String, Object>> editArgs = (List<Map<String, Object>>) args.get("edits");
                List<PatchEdit> edits = new ArrayList<>();
                for (Map<String, Object> ed : editArgs) {
                    edits.add(new PatchEdit(
                            (String) ed.get("expectedText"),
                            (String) ed.get("replacementText")
                    ));
                }

                // Capture a baseline snapshot to pass to the applier so the
                // WorkspaceGuard can perform a more robust freshness check.
                RepositorySnapshot baseline = null;
                try {
                    baseline = gitService.captureSnapshot(WORKSPACE_ROOT);
                } catch (Exception e) {
                    // best-effort: if snapshot capture fails, continue with null baseline
                }

                PatchOperation patch = new PatchOperation(
                        UUID.randomUUID().toString(),
                        path,
                        edits,
                        "Applied by tool",
                        null,
                        baseline
                );

                AppliedPatch result = applier.apply(patch);

                // build richer outputs from the applied change evidence
                String beforeHash = null;
                String afterHash = null;
                int editsApplied = patch.edits().size();
                if (result.changes() != null && !result.changes().changes().isEmpty()) {
                    var cr = result.changes().changes().get(0);
                    beforeHash = cr.beforeHash();
                    afterHash = cr.afterHash();
                }

                String finalBefore = beforeHash;
                String finalAfter = afterHash;

                return new ToolResult() {
                    @Override
                    public Map<String, Object> getOutputs() {
                        return Map.of(
                                "status", "success",
                                "file", path,
                                "editsApplied", editsApplied,
                                "beforeHash", finalBefore,
                                "afterHash", finalAfter
                        );
                    }

                    @Override
                    public boolean isSuccess() {
                        return true;
                    }

                    @Override
                    public String getErrorMessage() {
                        return null;
                    }

                    @Override
                    public ResourceId id() {
                        return ResourceId.from(Id.random(), ResourceType.fromString("tool-result"));
                    }

                    @Override
                    public ResourceType type() {
                        return ResourceType.fromString("tool-result");
                    }

                    @Override
                    public Metadata metadata() {
                        return Metadata.empty();
                    }
                };
            } catch (Exception e) {
                return new ToolResult() {
                    @Override
                    public Map<String, Object> getOutputs() {
                        return Collections.emptyMap();
                    }

                    @Override
                    public boolean isSuccess() {
                        return false;
                    }

                    @Override
                    public String getErrorMessage() {
                        return e.getMessage();
                    }

                    @Override
                    public ResourceId id() {
                        return ResourceId.from(Id.random(), ResourceType.fromString("tool-result"));
                    }

                    @Override
                    public ResourceType type() {
                        return ResourceType.fromString("tool-result");
                    }

                    @Override
                    public Metadata metadata() {
                        return Metadata.empty();
                    }
                };
            }
        });
    }

    @Override
    public ResourceId id() {
        return ResourceId.from(Id.random(), ResourceType.fromString("tool"));
    }

    @Override
    public ResourceType type() {
        return ResourceType.fromString("tool");
    }

    @Override
    public Metadata metadata() {
        return Metadata.empty();
    }
}
