package tech.kayys.aljabr.skill;

import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.identity.ResourceId;
import tech.kayys.wayang.resource.ResourceType;
import tech.kayys.wayang.extension.Metadata;
import tech.kayys.wayang.tool.ToolDescriptor;

public abstract class AljabrTool implements Tool {
    @Override
    public tech.kayys.wayang.identity.ResourceId id() {
        return tech.kayys.wayang.identity.ResourceId.from(tech.kayys.wayang.extension.Id.random(), new tech.kayys.wayang.resource.ResourceType.Tool());
    }

    @Override
    public tech.kayys.wayang.resource.ResourceType type() {
        return new tech.kayys.wayang.resource.ResourceType.Tool();
    }

    @Override
    public Metadata metadata() {
        return Metadata.builder().name(descriptor().name()).description(descriptor().description()).build();
    }
}
