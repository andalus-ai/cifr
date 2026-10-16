package tech.kayys.aljabr.engine.task.runtime;

import tech.kayys.aljabr.engine.task.TaskPhase;

public record TransitionDecision(TaskPhase nextPhase, String reason) {
    public static TransitionDecision to(TaskPhase phase, String reason) {
        return new TransitionDecision(phase, reason);
    }
}
