package tech.kayys.cifr.engine.tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ToolRequest(
    String toolName,
    Map<String, Object> arguments,
    Optional<String> reason,
    List<String> supportingEvidence
) {}
