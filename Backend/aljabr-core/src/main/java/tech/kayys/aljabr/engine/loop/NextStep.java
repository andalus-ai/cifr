package tech.kayys.aljabr.engine.loop;

import java.util.Optional;

/**
 * Scheduled operational step chosen by AgentScheduler.
 */
public record NextStep(
    NextStepType type,
    String reason,
    Optional<String> targetId
) {
    public static NextStep decisionCycle(String reason) {
        return new NextStep(NextStepType.RUN_DECISION_CYCLE, reason, Optional.empty());
    }

    public static NextStep executeAction(String actionId, String reason) {
        return new NextStep(NextStepType.EXECUTE_PENDING_ACTION, reason, Optional.of(actionId));
    }

    public static NextStep complete(String reason) {
        return new NextStep(NextStepType.COMPLETE, reason, Optional.empty());
    }

    public static NextStep fail(String reason) {
        return new NextStep(NextStepType.FAIL, reason, Optional.empty());
    }
}
