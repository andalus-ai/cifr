package tech.kayys.aljabr.engine.tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ToolRequest(
    String toolName,
    Map<String, Object> arguments,
    Optional<String> reason,
    List<String> supportingEvidence
) {}
