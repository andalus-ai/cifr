package tech.kayys.aljabr.engine.tools;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.andalus.extension.Id;
import tech.kayys.andalus.extension.Metadata;
import tech.kayys.andalus.identity.ResourceId;
import tech.kayys.andalus.resource.BaseResource;
import tech.kayys.andalus.resource.ResourceType;
import tech.kayys.andalus.tool.ToolContext;
import tech.kayys.andalus.tool.ToolInvocation;
import tech.kayys.andalus.tool.ToolResult;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class GitDiffTool extends AbstractGitTool {

    private final Path workspace = Paths.get(System.getProperty("user.dir"));

    public GitDiffTool() {
        super(new ResourceId.ToolId(Id.random()), Metadata.builder().name("git.diff").description("Runs git diff for a specific path").build());
    }

    @Override protected String getToolName() { return "git.diff"; }
    @Override protected String getToolDescription() { return "Runs git diff for a specific path"; }
    @Override protected Map<String, Object> getToolInputSchema() { return Map.of("path", "string"); }

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, ToolContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String path = (String) invocation.arguments().getOrDefault("path", ".");
                ProcessBuilder builder = new ProcessBuilder("git", "diff", path);
                builder.directory(workspace.toFile());
                Process process = builder.start();
                String output = new String(process.getInputStream().readAllBytes());
                process.waitFor();
                
                class DiffToolResult extends BaseResource implements ToolResult {
                    public DiffToolResult(Id id) {
                        super(new ResourceId.CustomId(id, new ResourceType.Custom("result")), Metadata.empty());
                    }
                    @Override public Map<String, Object> getOutputs() { return Map.of("output", output); }
                    @Override public boolean isSuccess() { return true; }
                    @Override public String getErrorMessage() { return null; }
                }
                return new DiffToolResult(Id.random());
            } catch (Exception e) {
                class ErrorToolResult extends BaseResource implements ToolResult {
                    public ErrorToolResult(Id id) {
                        super(new ResourceId.CustomId(id, new ResourceType.Custom("error")), Metadata.empty());
                    }
                    @Override public Map<String, Object> getOutputs() { return Map.of(); }
                    @Override public boolean isSuccess() { return false; }
                    @Override public String getErrorMessage() { return e.getMessage(); }
                }
                return new ErrorToolResult(Id.random());
            }
        });
    }
}
