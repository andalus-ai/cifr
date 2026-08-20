package tech.kayys.aljabr.engine.tool;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {
    Optional<AljabrTool> find(String name);
    List<ToolDescriptor> descriptors();
    boolean contains(String name);
}
