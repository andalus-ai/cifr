package tech.kayys.cifr.engine.verification.graph;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic completion decision based only on execution-bound evidence.
 * This policy is deliberately separate from the legacy descriptive evidence graph.
 */
@ApplicationScoped
public class EvidenceCompletionPolicy {

    public CompletionDecision evaluate(
            String taskId,
            String workspaceId,
            String workspaceFingerprint,
            Set<String> requiredCheckIds,
            List<ExecutionEvidence> evidence) {

        if (isBlank(taskId) || isBlank(workspaceId) || isBlank(workspaceFingerprint)
                || requiredCheckIds == null || requiredCheckIds.isEmpty() || evidence == null) {
            return CompletionDecision.NEED_MORE_VERIFICATION;
        }

        Set<String> required = new HashSet<>();
        for (String checkId : requiredCheckIds) {
            if (isBlank(checkId) || !required.add(checkId)) {
                return CompletionDecision.BLOCKED;
            }
        }

        Map<String, ExecutionEvidence> byCheck = new HashMap<>();
        for (ExecutionEvidence item : evidence) {
            if (item == null) {
                return CompletionDecision.BLOCKED;
            }
            if (required.contains(item.checkId())
                    && byCheck.putIfAbsent(item.checkId(), item) != null) {
                // Conflicting or duplicated results are ambiguous; never pick one silently.
                return CompletionDecision.BLOCKED;
            }
        }

        for (String checkId : required) {
            ExecutionEvidence result = byCheck.get(checkId);
            if (result == null) {
                return CompletionDecision.NEED_MORE_VERIFICATION;
            }
            if (!result.taskId().equals(taskId)
                    || !result.workspaceId().equals(workspaceId)
                    || !result.workspaceFingerprint().equals(workspaceFingerprint)) {
                return CompletionDecision.NEED_MORE_VERIFICATION;
            }
            if (result.status() == ExecutionCheckStatus.FAILED) {
                return CompletionDecision.NEED_REPAIR;
            }
            if (!result.provesPassFor(taskId, workspaceId, workspaceFingerprint, checkId)) {
                return CompletionDecision.NEED_MORE_VERIFICATION;
            }
        }

        return CompletionDecision.COMPLETE;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
