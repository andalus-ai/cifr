package tech.kayys.cifr.engine.tools;

import tech.kayys.andalus.descriptor.CapabilityDescriptor;
import tech.kayys.andalus.descriptor.ParameterDescriptor;
import tech.kayys.andalus.extension.Id;
import tech.kayys.andalus.extension.Metadata;
import tech.kayys.andalus.identity.ResourceId;
import tech.kayys.andalus.resource.BaseResource;
import tech.kayys.andalus.resource.ResourceType;
import tech.kayys.andalus.tool.Tool;
import tech.kayys.andalus.tool.ToolDescriptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractGitTool extends BaseResource implements Tool {

    protected AbstractGitTool(ResourceId id, Metadata metadata) {
        super(id, metadata);
    }

    protected abstract String getToolName();
    protected abstract String getToolDescription();
    protected abstract Map<String, Object> getToolInputSchema();

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor() {
            @Override public String name() { return getToolName(); }
            @Override public String description() { return getToolDescription(); }
            @Override public String version() { return "1.0"; }
            @Override public Map<String, Object> inputSchema() { return getToolInputSchema(); }
            
            @Override public ResourceId id() { return AbstractGitTool.this.id(); }
            @Override public Metadata metadata() { return AbstractGitTool.this.metadata(); }
            @Override public ResourceType type() { return AbstractGitTool.this.type(); }
            
            @Override public Set<String> tags() { return Collections.emptySet(); }
            @Override public Set<String> categories() { return Collections.emptySet(); }
            @Override public Map<String, ParameterDescriptor> inputs() { return Collections.emptyMap(); }
            @Override public Map<String, ParameterDescriptor> outputs() { return Collections.emptyMap(); }
            @Override public List<CapabilityDescriptor> capabilities() { return Collections.emptyList(); }
        };
    }
}
