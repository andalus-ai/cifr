package tech.kayys.cifr.engine.phase;

import tech.kayys.cifr.engine.gate.CompletionClaim;
import java.util.List;

public record TaskExecutionResult(
    String proposedStatus,
    String summary,
    List<String> evidenceIds,
    CompletionClaim claim
) {}
