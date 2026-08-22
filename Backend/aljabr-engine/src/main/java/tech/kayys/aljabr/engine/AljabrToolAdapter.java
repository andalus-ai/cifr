package tech.kayys.aljabr.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.aljabr.engine.loop.CodingPolicy;
import tech.kayys.aljabr.engine.tool.AljabrTool;
import tech.kayys.aljabr.engine.tool.ToolExecutionContext;
import tech.kayys.aljabr.engine.tool.ToolRequest;
import tech.kayys.wayang.descriptor.CapabilityDescriptor;
import tech.kayys.wayang.descriptor.ParameterDescriptor;
import tech.kayys.wayang.extension.Id;
import tech.kayys.wayang.extension.Metadata;
import tech.kayys.wayang.identity.ResourceId;
import tech.kayys.wayang.resource.BaseResource;
import tech.kayys.wayang.resource.ResourceType;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.tool.ToolContext;
import tech.kayys.wayang.tool.ToolDescriptor;
import tech.kayys.wayang.tool.ToolInvocation;
import tech.kayys.wayang.tool.ToolResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Adapts an AljabrTool into a tech.kayys.wayang.tool.Tool SPI instance.
 */
public class AljabrToolAdapter extends BaseResource implements Tool {

    private final AljabrTool delegate;
    private final ObjectMapper mapper = new ObjectMapper();

    public AljabrToolAdapter(AljabrTool delegate) {
        super(new ResourceId.ToolId(Id.random()), 
              Metadata.builder()
                      .name(delegate.descriptor() != null ? delegate.descriptor().name() : "aljabr-tool")
                      .description(delegate.descriptor() != null ? delegate.descriptor().description() : "")
                      .build());
        this.delegate = delegate;
    }

    public AljabrTool getDelegate() {
        return delegate;
    }

    @Override
    public ToolDescriptor descriptor() {
        tech.kayys.aljabr.engine.tool.ToolDescriptor d = delegate.descriptor();
        final String toolName = d != null ? d.name() : "unknown";
        final String toolDesc = d != null ? d.description() : "";
        final Map<String, Object> schema = d != null ? d.inputSchema() : Collections.emptyMap();

        return new ToolDescriptor() {
            @Override public String name() { return toolName; }
            @Override public String description() { return toolDesc; }
            @Override public String version() { return "1.0.0"; }
            @Override public Map<String, Object> inputSchema() { return schema; }
            
            @Override public ResourceId id() { return AljabrToolAdapter.this.id(); }
            @Override public Metadata metadata() { return AljabrToolAdapter.this.metadata(); }
            @Override public ResourceType type() { return AljabrToolAdapter.this.type(); }

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
        ToolExecutionContext execCtx = new ToolExecutionContext(
                "task-" + System.currentTimeMillis(),
                "workspace-default",
                CodingPolicy.defaultPolicy(),
                Optional.empty(),
                () -> {}
        );

        return delegate.execute(req, execCtx)
                .map(res -> {
                    Map<String, Object> outputs = new LinkedHashMap<>();
                    boolean success = res != null && res.isSuccess();
                    String summary = res != null ? res.getSummary() : "";
                    outputs.put("summary", summary);
                    outputs.put("success", success);
                    if (res instanceof tech.kayys.aljabr.engine.tool.fs.DirectoryListResult dlr) {
                        List<String> entryNames = dlr.entries().stream()
                                .map(e -> (e.isDirectory() ? "[DIR] " : "") + e.path())
                                .toList();
                        outputs.put("entries", entryNames);
                    } else if (res instanceof tech.kayys.aljabr.engine.tool.fs.ReadFileResult rfr) {
                        outputs.put("path", rfr.path());
                        String content = rfr.lines().stream()
                                .map(tech.kayys.aljabr.engine.tool.fs.SourceLine::text)
                                .collect(java.util.stream.Collectors.joining("\n"));
                        outputs.put("content", content);
                    } else if (res instanceof tech.kayys.aljabr.engine.tool.fs.FindResult fr) {
                        List<String> matchPaths = fr.entries().stream()
                                .map(tech.kayys.aljabr.engine.tool.fs.FindEntry::path)
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
