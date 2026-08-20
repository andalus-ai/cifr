package tech.kayys.aljabr.engine.action;

import java.util.List;
import java.util.Map;

/**
 * Concrete result produced by executing an action. Becomes input for Evidence and Verification.
 */
public record ActionResult(
    String executionId,
    ActionResultStatus status,
    String outputSummary,
    Map<String, Object> details,
    List<String> generatedEvidenceIds
) {
    public ActionResult {
        details = details != null ? Map.copyOf(details) : Map.of();
        generatedEvidenceIds = generatedEvidenceIds != null ? List.copyOf(generatedEvidenceIds) : List.of();
    }

    public boolean isSuccess() {
        return status == ActionResultStatus.SUCCESS;
    }

    public static ActionResult success(String executionId, String summary) {
        return new ActionResult(executionId, ActionResultStatus.SUCCESS, summary, Map.of(), List.of());
    }

    public static ActionResult failure(String executionId, String summary) {
        return new ActionResult(executionId, ActionResultStatus.FAILED, summary, Map.of(), List.of());
    }
}
