package tech.kayys.aljabr.engine.tools;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.wayang.extension.Id;
import tech.kayys.wayang.extension.Metadata;
import tech.kayys.wayang.identity.ResourceId;
import tech.kayys.wayang.resource.BaseResource;
import tech.kayys.wayang.resource.ResourceType;
import tech.kayys.wayang.tool.ToolContext;
import tech.kayys.wayang.tool.ToolInvocation;
import tech.kayys.wayang.tool.ToolResult;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class GitStatusTool extends AbstractGitTool {

    private final Path workspace = Paths.get(System.getProperty("user.dir"));

    public GitStatusTool() {
        super(new ResourceId.ToolId(Id.random()), Metadata.builder().name("git.status").description("Runs git status --porcelain").build());
    }

    @Override protected String getToolName() { return "git.status"; }
    @Override protected String getToolDescription() { return "Runs git status --porcelain"; }
    @Override protected Map<String, Object> getToolInputSchema() { return Map.of(); }

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, ToolContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder builder = new ProcessBuilder("git", "status", "--porcelain");
                builder.directory(workspace.toFile());
                Process process = builder.start();
                String output = new String(process.getInputStream().readAllBytes());
                process.waitFor();
                
                class StatusToolResult extends BaseResource implements ToolResult {
                    public StatusToolResult(Id id) {
                        super(new ResourceId.CustomId(id, new ResourceType.Custom("result")), Metadata.empty());
                    }
                    @Override public Map<String, Object> getOutputs() { return Map.of("output", output); }
                    @Override public boolean isSuccess() { return true; }
                    @Override public String getErrorMessage() { return null; }
                }
                return new StatusToolResult(Id.random());
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
