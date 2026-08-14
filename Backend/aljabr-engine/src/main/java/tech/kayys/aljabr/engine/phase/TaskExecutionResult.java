package tech.kayys.aljabr.engine.phase;

import tech.kayys.aljabr.engine.gate.CompletionClaim;
import java.util.List;

public record TaskExecutionResult(
    String proposedStatus,
    String summary,
    List<String> evidenceIds,
    CompletionClaim claim
) {}
