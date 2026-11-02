package tech.kayys.cifr.engine.tool;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {
    Optional<CifrTool> find(String name);
    List<ToolDescriptor> descriptors();
    boolean contains(String name);
}
