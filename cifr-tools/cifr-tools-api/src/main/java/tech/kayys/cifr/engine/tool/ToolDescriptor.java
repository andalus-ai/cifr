package tech.kayys.cifr.engine.tool;

import java.util.Map;
import java.util.Set;

public record ToolDescriptor(
    String name,
    String description,
    ToolCategory category,
    Map<String, Object> inputSchema,
    Set<ToolCapability> capabilities,
    ToolRisk risk
) {}
