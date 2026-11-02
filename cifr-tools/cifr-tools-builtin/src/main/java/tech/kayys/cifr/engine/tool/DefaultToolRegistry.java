package tech.kayys.cifr.engine.tool;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class DefaultToolRegistry implements ToolRegistry {

    private final Map<String, CifrTool> tools;

    public DefaultToolRegistry(jakarta.enterprise.inject.Instance<CifrTool> toolInstances) {
        this.tools = toolInstances.stream()
            .collect(Collectors.toMap(
                t -> t.descriptor().name(),
                Function.identity()
            ));
    }

    @Override
    public Optional<CifrTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    @Override
    public List<ToolDescriptor> descriptors() {
        return tools.values().stream()
            .map(CifrTool::descriptor)
            .collect(Collectors.toList());
    }

    @Override
    public boolean contains(String name) {
        return tools.containsKey(name);
    }
}
