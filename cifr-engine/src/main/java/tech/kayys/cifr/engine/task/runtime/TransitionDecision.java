package tech.kayys.cifr.engine.task.runtime;

import tech.kayys.cifr.engine.task.TaskPhase;

public record TransitionDecision(TaskPhase nextPhase, String reason) {
    public static TransitionDecision to(TaskPhase phase, String reason) {
        return new TransitionDecision(phase, reason);
    }
}
