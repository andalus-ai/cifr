package tech.kayys.aljabr.skill;

import tech.kayys.wayang.tool.ToolDescriptor;
import java.util.Map;
import java.util.HashMap;

public class AljabrToolDescriptor implements ToolDescriptor {
    private final String name;
    private final String description;

    public AljabrToolDescriptor(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new HashMap<>();
    }

    @Override
    public java.util.List<tech.kayys.wayang.descriptor.CapabilityDescriptor> capabilities() {
        return java.util.Collections.emptyList();
    }
    
    @Override
    public java.util.Set<String> tags() {
        return java.util.Collections.emptySet();
    }
    
    @Override
    public java.util.Set<String> categories() {
        return java.util.Collections.emptySet();
    }
    
    @Override
    public java.util.Map<String, tech.kayys.wayang.descriptor.ParameterDescriptor> inputs() {
        return java.util.Collections.emptyMap();
    }

    @Override
    public java.util.Map<String, tech.kayys.wayang.descriptor.ParameterDescriptor> outputs() {
        return java.util.Collections.emptyMap();
    }

    @Override
    public tech.kayys.wayang.identity.ResourceId id() {
        return tech.kayys.wayang.identity.ResourceId.from(tech.kayys.wayang.extension.Id.random(), new tech.kayys.wayang.resource.ResourceType.Descriptor());
    }

    @Override
    public tech.kayys.wayang.resource.ResourceType type() {
        return new tech.kayys.wayang.resource.ResourceType.Descriptor();
    }

    @Override
    public tech.kayys.wayang.extension.Metadata metadata() {
        return tech.kayys.wayang.extension.Metadata.builder().name(name).description(description).build();
    }
}
