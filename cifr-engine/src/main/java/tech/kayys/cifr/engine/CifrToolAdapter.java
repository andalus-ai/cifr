package tech.kayys.cifr.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.cifr.engine.loop.CodingPolicy;
import tech.kayys.cifr.engine.tool.CifrTool;
import tech.kayys.cifr.engine.tool.ToolExecutionContext;
import tech.kayys.cifr.engine.tool.ToolRequest;
import tech.kayys.andalus.descriptor.CapabilityDescriptor;
import tech.kayys.andalus.descriptor.ParameterDescriptor;
import tech.kayys.andalus.extension.Id;
import tech.kayys.andalus.extension.Metadata;
import tech.kayys.andalus.identity.ResourceId;
import tech.kayys.andalus.resource.BaseResource;
import tech.kayys.andalus.resource.ResourceType;
import tech.kayys.andalus.tool.Tool;
import tech.kayys.andalus.tool.ToolContext;
import tech.kayys.andalus.tool.ToolDescriptor;
import tech.kayys.andalus.tool.ToolInvocation;
import tech.kayys.andalus.tool.ToolResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Adapts an CifrTool into a tech.kayys.andalus.tool.Tool SPI instance.
 */
public class CifrToolAdapter extends BaseResource implements Tool {

    private final CifrTool delegate;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String workspacePath;

    public CifrToolAdapter(CifrTool delegate, String workspacePath) {
        super(new ResourceId.ToolId(Id.random()), 
              Metadata.builder()
                      .name(delegate.descriptor() != null ? delegate.descriptor().name() : "cifr-tool")
                      .description(delegate.descriptor() != null ? delegate.descriptor().description() : "")
                      .build());
        this.delegate = delegate;
        this.workspacePath = workspacePath;
    }

    public CifrTool getDelegate() {
        return delegate;
    }

    @Override
    public ToolDescriptor descriptor() {
        tech.kayys.cifr.engine.tool.ToolDescriptor d = delegate.descriptor();
        final String toolName = d != null ? d.name() : "unknown";
        final String toolDesc = d != null ? d.description() : "";
        final Map<String, Object> schema = d != null ? d.inputSchema() : Collections.emptyMap();

        return new ToolDescriptor() {
            @Override public String name() { return toolName; }
            @Override public String description() { return toolDesc; }
            @Override public String version() { return "1.0.0"; }
            @Override public Map<String, Object> inputSchema() { return schema; }
            
            @Override public ResourceId id() { return CifrToolAdapter.this.id(); }
            @Override public Metadata metadata() { return CifrToolAdapter.this.metadata(); }
            @Override public ResourceType type() { return CifrToolAdapter.this.type(); }

            @Override public Set<String> tags() { return Collections.emptySet(); }
            @Override public Set<String> categories() { return Collections.emptySet(); }
            @Override public Map<String, ParameterDescriptor> inputs() { return Collections.emptyMap(); }
            @Override public Map<String, ParameterDescriptor> outputs() { return Collections.emptyMap(); }
            @Override public List<CapabilityDescriptor> capabilities() { return Collections.emptyList(); }
        };
    }

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, ToolContext context) {
        Map<String, Object> args = invocation != null && invocation.arguments() != null ? invocation.arguments() : Collections.emptyMap();
        ToolRequest req = new ToolRequest(invocation != null ? invocation.name() : "", args, Optional.empty(), Collections.emptyList());
        Path workspaceRoot = workspacePath == null || workspacePath.isBlank()
                ? null : Path.of(workspacePath).toAbsolutePath().normalize();
        String workspaceId = workspaceRoot != null ? workspaceRoot.toString() : "workspace-unresolved";
        ToolExecutionContext execCtx = ToolExecutionContext.of(
                "task-" + System.currentTimeMillis(),
                workspaceId,
                CodingPolicy.defaultPolicy(),
                Optional.empty(),
                () -> {},
                workspaceRoot
        );

        return delegate.execute(req, execCtx)
                .map(res -> {
                    Map<String, Object> outputs = new LinkedHashMap<>();
                    boolean success = res != null && res.isSuccess();
                    String summary = res != null ? res.getSummary() : "";
                    outputs.put("summary", summary);
                    outputs.put("success", success);
                    if (res instanceof tech.kayys.cifr.engine.tool.fs.DirectoryListResult dlr) {
                        List<String> entryNames = dlr.entries().stream()
                                .map(e -> (e.isDirectory() ? "[DIR] " : "") + e.path())
                                .toList();
                        outputs.put("entries", entryNames);
                    } else if (res instanceof tech.kayys.cifr.engine.tool.fs.ReadFileResult rfr) {
                        outputs.put("path", rfr.path());
                        String content = rfr.lines().stream()
                                .map(tech.kayys.cifr.engine.tool.fs.SourceLine::text)
                                .collect(java.util.stream.Collectors.joining("\n"));
                        outputs.put("content", content);
                    } else if (res instanceof tech.kayys.cifr.engine.tool.fs.FindResult fr) {
                        List<String> matchPaths = fr.entries().stream()
                                .map(tech.kayys.cifr.engine.tool.fs.FindEntry::path)
                                .toList();
                        outputs.put("matches", matchPaths);
                    } else if (res != null) {
                        try {
                            outputs.put("data", mapper.convertValue(res, Map.class));
                        } catch (Exception ignored) {}
                    }
                    
                    class OutputToolResult extends BaseResource implements ToolResult {
                        public OutputToolResult(Id id) {
                            super(new ResourceId.CustomId(id, new ResourceType.Custom("result")), Metadata.empty());
                        }
                        @Override public Map<String, Object> getOutputs() { return outputs; }
                        @Override public boolean isSuccess() { return success; }
                        @Override public String getErrorMessage() { return success ? null : summary; }
                    }
                    return (ToolResult) new OutputToolResult(Id.random());
                })
                .subscribeAsCompletionStage();
    }
}
